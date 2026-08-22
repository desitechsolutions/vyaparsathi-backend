package com.desitech.vyaparsathi.payroll.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableGlobalMethodSecurity(
    securedEnabled = true,
    jsr250Enabled = true,
    prePostEnabled = true
)
public class PayrollSecurityConfig {

    @Bean
    public SecurityFilterChain payrollSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors().and()
            .csrf().disable()
            .authorizeRequests()
            // Public endpoints
            .antMatchers("/api/payroll/health", "/api/payroll/version").permitAll()
            // Admin endpoints
            .antMatchers(HttpMethod.GET, "/api/payroll/runs/**").hasAnyRole("ADMIN", "PAYROLL_ADMIN", "MANAGER")
            .antMatchers(HttpMethod.POST, "/api/payroll/runs/**").hasAnyRole("ADMIN", "PAYROLL_ADMIN")
            .antMatchers(HttpMethod.PUT, "/api/payroll/runs/**").hasAnyRole("ADMIN", "PAYROLL_ADMIN")
            .antMatchers(HttpMethod.DELETE, "/api/payroll/runs/**").hasRole("ADMIN")
            // Manager approval endpoints
            .antMatchers(HttpMethod.POST, "/api/payroll/runs/*/approve").hasAnyRole("ADMIN", "PAYROLL_ADMIN", "MANAGER")
            .antMatchers(HttpMethod.POST, "/api/payroll/runs/*/disburse").hasAnyRole("ADMIN", "PAYROLL_ADMIN")
            // Employee self-service
            .antMatchers(HttpMethod.GET, "/api/payroll/employee/**").hasRole("EMPLOYEE")
            .antMatchers(HttpMethod.POST, "/api/payroll/employee/advance-requests").hasRole("EMPLOYEE")
            // Audit endpoints
            .antMatchers("/api/payroll/audit/**").hasRole("ADMIN")
            // Default: require authentication
            .anyRequest().authenticated()
            .and()
            .httpBasic();

        return http.build();
    }

    @Bean
    public CorsConfigurationSource payrollCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("https://app.vyaparsathi.com", "https://admin.vyaparsathi.com"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/payroll/**", configuration);
        return source;
    }
}
