package com.desitech.vyaparsathi.common.configs;

import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.auth.security.CustomUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    public WebSocketConfig(JwtUtil jwtUtil,
                           CustomUserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Value("${app.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    // ================= ENDPOINT =================
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        String[] origins = allowedOrigins.split(",");

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins)
                .addInterceptors(httpSessionHandshakeInterceptor())
                .withSockJS();
    }

    // ================= BROKER =================
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    // ================= HANDSHAKE =================
    @Bean
    public HandshakeInterceptor httpSessionHandshakeInterceptor() {

        return new HandshakeInterceptor() {

            @Override
            public boolean beforeHandshake(ServerHttpRequest request,
                                           ServerHttpResponse response,
                                           WebSocketHandler wsHandler,
                                           Map<String, Object> attributes) {

                try {
                    Long shopId = TenantContext.getCurrentShopId();
                    logger.info("shop id from tenant context : {}",shopId);


                    if (shopId != null) {
                        attributes.put("shopId", shopId);
                        logger.debug("WS Handshake captured shopId {}", shopId);
                    }

                } catch (Exception e) {
                    logger.error("Handshake shop capture failed", e);
                }

                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request,
                                       ServerHttpResponse response,
                                       WebSocketHandler wsHandler,
                                       Exception exception) {
            }
        };
    }

    // ================= CHANNEL INTERCEPTOR =================
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {

        registration.interceptors(new ChannelInterceptor() {

            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {

                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) return message;

                // ===== CONNECT =====
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticateWebSocket(accessor);
                }

                // ===== SUBSCRIBE =====
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    handleSubscriptionWithSecurity(accessor);
                }

                // ===== SEND =====
                if (StompCommand.SEND.equals(accessor.getCommand())) {
                    propagateTenantFromSession(accessor);
                }

                return message;
            }

            // ================= AUTH =================
            private void authenticateWebSocket(StompHeaderAccessor accessor) {

                String authHeader = accessor.getFirstNativeHeader("Authorization");

                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    logger.warn("WS CONNECT without Authorization");
                    throw new AccessDeniedException("Missing Authorization header");
                }

                try {

                    String jwt = authHeader.substring(7);

                    if (!jwtUtil.validateToken(jwt)) {
                        throw new AccessDeniedException("Invalid WS Token");
                    }

                    String username = jwtUtil.extractUsername(jwt);
                    Long shopId = jwtUtil.extractShopId(jwt);

                    UserDetails userDetails =
                            userDetailsService.loadUserByUsername(username);

                    Authentication auth =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    accessor.setUser(auth);

                    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

                    if (sessionAttributes != null) {
                        if (shopId != null) {
                            sessionAttributes.put("shopId", shopId);
                        } else {
                            sessionAttributes.remove("shopId");
                        }
                    }

                    logger.info("WS Authentication success user={}, shopId={}",
                            username, shopId);

                } catch (Exception e) {
                    logger.error("WS Authentication failed", e);
                    throw new AccessDeniedException("WS Authentication failed");
                }
            }

            // ================= SUBSCRIBE SECURITY =================
            private void handleSubscriptionWithSecurity(StompHeaderAccessor accessor) {
                String destination = accessor.getDestination();
                if (destination == null) return;

                boolean isSuperAdmin = checkIsSuperAdmin(accessor);

                // 1. Allow Admin topics (Support AND Typing)
                if (destination.startsWith("/topic/admin/")) {
                    if (!isSuperAdmin) {
                        throw new AccessDeniedException("Admin topic access denied");
                    }
                    logger.info("Super Admin subscribed to: {}", destination);
                }
                // 2. Allow Shop topics
                else if (destination.startsWith("/topic/shop/")) {
                    if (!isSuperAdmin) {
                        validateShopId(accessor, destination);
                    } else {
                        // Admin can subscribe to any shop topic
                        TenantContext.setCurrentShopId(null);
                    }
                }
            }

            private boolean checkIsSuperAdmin(StompHeaderAccessor accessor) {

                Object user = accessor.getUser();

                if (user instanceof Authentication auth) {
                    return auth.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
                }

                return false;
            }

            private void validateShopId(StompHeaderAccessor accessor, String destination) {

                Long sessionShopId = null;

                if (accessor.getSessionAttributes() != null) {
                    sessionShopId = (Long) accessor.getSessionAttributes().get("shopId");
                }

                String[] parts = destination.split("/");

                if (parts.length >= 4) {

                    String topicShopId = parts[3];

                    if (sessionShopId == null ||
                            !sessionShopId.toString().equals(topicShopId)) {

                        logger.warn("Shop security violation sessionShop={} topicShop={}",
                                sessionShopId, topicShopId);

                        throw new AccessDeniedException("Unauthorized shop topic");
                    }

                    TenantContext.setCurrentShopId(sessionShopId);
                }
            }

            // ================= SEND TENANT PROPAGATION =================
            private void propagateTenantFromSession(StompHeaderAccessor accessor) {

                try {

                    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

                    if (sessionAttributes == null) {
                        TenantContext.setCurrentShopId(null);
                        return;
                    }

                    Long shopId = (Long) sessionAttributes.get("shopId");

                    TenantContext.setCurrentShopId(shopId);

                    logger.debug("WS SEND Tenant set shopId={}", shopId);

                } catch (Exception e) {
                    logger.error("Failed to propagate tenant on SEND", e);
                }
            }
        });
    }

    // ================= TRANSPORT =================
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {

        registration.setSendTimeLimit(20000)
                .setSendBufferSizeLimit(512 * 1024)
                .setMessageSizeLimit(128 * 1024);
    }
}
