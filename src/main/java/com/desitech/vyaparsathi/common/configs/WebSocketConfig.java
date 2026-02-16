package com.desitech.vyaparsathi.common.configs;

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
import org.springframework.security.access.AccessDeniedException;
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

    @Value("${allowed.origins:http://localhost:3000}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins)
                .addInterceptors(httpSessionHandshakeInterceptor()) // Capture tenant here
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * This interceptor runs during the initial HTTP upgrade to WebSocket.
     * We grab the ShopID from the current TenantContext and save it in the
     * WebSocket session attributes.
     */
    @Bean
    public HandshakeInterceptor httpSessionHandshakeInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler wsHandler, Map<String, Object> attributes) {
                Long shopId = TenantContext.getCurrentShopId();
                if (shopId != null) {
                    attributes.put("shopId", shopId);
                }
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Exception exception) {}
        };
    }

    @Bean
    public ChannelInterceptor tenantChannelInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

                if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination == null) return message;

                    // 1. Handle Shop-wide Broadcasts
                    if (destination.startsWith("/topic/shop/")) {
                        validateShopSubscription(accessor, destination);
                    }

                    // 2. Handle Private User Notifications
                    else if (destination.startsWith("/user/queue/notifications")) {
                        validateUserSubscription(accessor);
                    }
                }
                return message;
            }

            private void validateShopSubscription(StompHeaderAccessor accessor, String destination) {
                Long currentShopId = (accessor.getSessionAttributes() != null)
                        ? (Long) accessor.getSessionAttributes().get("shopId") : null;

                String[] parts = destination.split("/");
                if (parts.length >= 4) {
                    String shopIdFromTopic = parts[3];
                    if (currentShopId == null || !currentShopId.toString().equals(shopIdFromTopic)) {
                        logger.warn("Tenant Mismatch! User Shop: {}, Topic Shop: {}", currentShopId, shopIdFromTopic);
                        throw new AccessDeniedException("Unauthorized shop subscription.");
                    }
                    TenantContext.setCurrentShopId(currentShopId);
                }
            }

            private void validateUserSubscription(StompHeaderAccessor accessor) {
                // Spring Security automatically handles the /user/ prefix routing,
                // but we can add an extra check here if needed to ensure the session
                // is authenticated.
                if (accessor.getUser() == null) {
                    throw new AccessDeniedException("Authentication required for private notifications.");
                }
            }
        };
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(tenantChannelInterceptor());
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(15 * 1000)
                .setSendBufferSizeLimit(512 * 1024)
                .setMessageSizeLimit(128 * 1024);
    }
}