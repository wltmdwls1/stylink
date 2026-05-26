package com.stylink.bo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylink.bo.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // REST API는 CSRF 불필요 (쿠키 세션 없음)
                .csrf(AbstractHttpConfigurer::disable)
                // JWT 기반 → 세션 생성 안 함
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Swagger UI 접근 허용
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        // 토큰 없음 → 401
                        .authenticationEntryPoint((request, response, e) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(Map.of("success", false, "message", "인증이 필요합니다."))
                            );
                        })
                        // 토큰 있지만 권한 없음 → 403
                        .accessDeniedHandler((request, response, e) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(Map.of("success", false, "message", "접근 권한이 없습니다."))
                            );
                        })
                )
                // 인증 필터 앞에 JWT 필터 삽입
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
