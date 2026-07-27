package com.jetski.shared.config;

import com.jetski.shared.authorization.ABACAuthorizationInterceptor;
import com.jetski.shared.authorization.PlatformScopeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração Web MVC para registrar interceptors.
 *
 * Registra:
 * - PlatformScopeInterceptor: barreira de escopo das rotas /v1/platform/** (antes do ABAC)
 * - ABACAuthorizationInterceptor: autorização ABAC via OPA
 *
 * @author Jetski Team
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ABACAuthorizationInterceptor abacAuthorizationInterceptor;
    private final PlatformScopeInterceptor platformScopeInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // Defesa em profundidade: nega /v1/platform/** para quem não é operador de
        // plataforma ANTES de consultar o OPA — se as políticas falharem, aqui ainda barra.
        registry.addInterceptor(platformScopeInterceptor)
            .order(-10)
            .addPathPatterns("/v1/platform/**", "/api/v1/platform/**");

        registry.addInterceptor(abacAuthorizationInterceptor)
            .order(0) // autorização SEMPRE antes do gating de módulo (ver ModuloPlanoInterceptor)
            .addPathPatterns("/**")  // Aplica a todos os endpoints (context-path /api já está no request)
            .excludePathPatterns(
                "/v1/auth-test/public",  // Endpoint público
                "/v1/pdf/**",            // Abertura de PDF por token (uso único, público)
                "/actuator/**",          // Actuator (health, metrics)
                "/swagger-ui/**",        // Swagger UI
                "/v3/api-docs/**"        // OpenAPI docs
            );
    }
}
