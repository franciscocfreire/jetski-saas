package com.jetski.shared.config;

import com.jetski.shared.authorization.ABACAuthorizationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração Web MVC para registrar interceptors.
 *
 * Registra:
 * - ABACAuthorizationInterceptor: autorização ABAC via OPA
 *
 * @author Jetski Team
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ABACAuthorizationInterceptor abacAuthorizationInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(abacAuthorizationInterceptor)
            .addPathPatterns("/v1/**")  // Aplica a todos os endpoints de API
            .excludePathPatterns(
                "/v1/auth-test/public",  // Endpoint público
                "/actuator/**",          // Actuator (health, metrics)
                "/swagger-ui/**",        // Swagger UI
                "/v3/api-docs/**"        // OpenAPI docs
            );
    }
}
