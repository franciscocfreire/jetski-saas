package com.jetski.shared.security;

import com.jetski.shared.internal.FilterChainExceptionFilter;
import com.jetski.shared.internal.JwtAuthenticationConverter;
import com.jetski.shared.internal.TenantFilter;
import com.jetski.shared.observability.BusinessMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuração de segurança do Spring Security para Jetski SaaS Multi-Tenant.
 *
 * Arquitetura de Segurança:
 * 1. OAuth2 Resource Server (JWT) - valida tokens do Keycloak
 * 2. TenantFilter (custom) - valida tenant_id do JWT vs header X-Tenant-Id
 * 3. ABAC (Attribute-Based Access Control) - autorização via OPA com ABACAuthorizationInterceptor
 * 4. CORS configurado para permitir origins por tenant
 * 5. Stateless - sem sessões HTTP, apenas JWT
 *
 * Fluxo de Autenticação/Autorização:
 * Request → CORS → OAuth2 (valida JWT) → TenantFilter (valida tenant) → ABACAuthorizationInterceptor (OPA) → Controller
 *
 * Nota: @EnableMethodSecurity DESABILITADO - não usamos mais @PreAuthorize, apenas ABAC via OPA.
 *
 * @author Jetski Team
 */
@Configuration
@EnableWebSecurity
// @EnableMethodSecurity - DESABILITADO: migrado para ABAC via ABACAuthorizationInterceptor
public class SecurityConfig {

    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final TenantAccessValidator tenantAccessValidator;
    private final FilterChainExceptionFilter filterChainExceptionFilter;
    private final BusinessMetrics businessMetrics;

    public SecurityConfig(
            JwtAuthenticationConverter jwtAuthenticationConverter,
            TenantAccessValidator tenantAccessValidator,
            FilterChainExceptionFilter filterChainExceptionFilter,
            BusinessMetrics businessMetrics) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.tenantAccessValidator = tenantAccessValidator;
        this.filterChainExceptionFilter = filterChainExceptionFilter;
        this.businessMetrics = businessMetrics;
    }

    /**
     * TenantFilter bean - adicionado programaticamente à protected chain
     */
    @Bean
    public TenantFilter tenantFilter() {
        return new TenantFilter(tenantAccessValidator, businessMetrics);
    }

    /**
     * JwtDecoder customizado que aceita múltiplos issuers válidos.
     *
     * Necessário para ambiente local onde:
     * - Browser/Postman usam http://localhost:8081 (issuer no JWT)
     * - Mobile usa http://172.30.197.110:8081 (IP do WSL)
     *
     * Ambos apontam para o mesmo Keycloak, mas o issuer no JWT vem como localhost.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // Lista de issuers válidos (ambiente local)
        List<String> validIssuers = List.of(
            "http://localhost:8081/realms/jetski-saas",
            "http://172.30.197.110:8081/realms/jetski-saas"
        );

        // Usar o primeiro issuer para buscar as chaves públicas (JWK Set)
        String jwkSetUri = "http://localhost:8081/realms/jetski-saas/protocol/openid-connect/certs";

        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        // Criar validadores para cada issuer
        List<OAuth2TokenValidator<Jwt>> issuerValidators = validIssuers.stream()
            .map(JwtIssuerValidator::new)
            .map(v -> (OAuth2TokenValidator<Jwt>) v)
            .toList();

        // Validador customizado que aceita qualquer um dos issuers (OR logic)
        OAuth2TokenValidator<Jwt> multiIssuerValidator = jwt -> {
            for (OAuth2TokenValidator<Jwt> validator : issuerValidators) {
                var result = validator.validate(jwt);
                if (!result.hasErrors()) {
                    return result; // Aceita se qualquer issuer for válido
                }
            }
            // Se nenhum issuer for válido, retorna erro do primeiro validador
            return issuerValidators.get(0).validate(jwt);
        };

        // Combinar timestamp validation + multi-issuer validation
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(),
            multiIssuerValidator
        );

        jwtDecoder.setJwtValidator(validator);
        return jwtDecoder;
    }

    /**
     * SecurityFilterChain para endpoints PÚBLICOS (sem autenticação).
     * Este chain tem @Order(1) para rodar ANTES do chain principal.
     *
     * Endpoints públicos não passam pelo OAuth2 Resource Server.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        http
            // Matcher apenas para endpoints públicos (usando Ant matchers explícitos)
            // Ant matchers não dependem de MVC handler registration (ao contrário de MVC matchers)
            .securityMatcher(new OrRequestMatcher(
                new AntPathRequestMatcher("/actuator/health"),
                new AntPathRequestMatcher("/actuator/info"),
                new AntPathRequestMatcher("/actuator/prometheus"),  // Prometheus metrics
                new AntPathRequestMatcher("/actuator/metrics/**"),  // Micrometer metrics
                new AntPathRequestMatcher("/swagger-ui.html"),
                new AntPathRequestMatcher("/swagger-ui/**"),
                new AntPathRequestMatcher("/v3/api-docs/**"),
                new AntPathRequestMatcher("/v1/auth-test/public"),
                new AntPathRequestMatcher("/v1/auth/complete-activation"),  // Account activation (Option 2: temp password flow)
                new AntPathRequestMatcher("/v1/auth/magic-activate"),       // Account activation (Magic link JWT - one-click UX)
                new AntPathRequestMatcher("/v1/storage/local/**")           // Local storage endpoints (simulated presigned URLs)
            ))

            // Exception filter FIRST to catch all downstream exceptions
            .addFilterBefore(filterChainExceptionFilter, UsernamePasswordAuthenticationFilter.class)

            // CORS configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // CSRF disabled
            .csrf(csrf -> csrf.disable())

            // Stateless
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Permitir TUDO sem autenticação
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );

        return http.build();
    }

    /**
     * SecurityFilterChain principal para endpoints PROTEGIDOS (requer autenticação).
     * Este chain tem @Order(2) para rodar DEPOIS do chain público.
     *
     * Segurança Configurada:
     * - OAuth2 Resource Server com JWT do Keycloak
     * - CORS habilitado
     * - CSRF desabilitado (stateless API com JWT)
     * - Sessões stateless (sem cookies)
     * - Autorização baseada em paths + method-level (@PreAuthorize)
     */
    @Bean
    @Order(2)
    public SecurityFilterChain protectedFilterChain(HttpSecurity http) throws Exception {
        http
            // Exception filter FIRST to catch all downstream exceptions
            .addFilterBefore(filterChainExceptionFilter, UsernamePasswordAuthenticationFilter.class)

            // CORS configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // CSRF disabled (JWT é stateless, CSRF protection não necessária)
            .csrf(csrf -> csrf.disable())

            // Stateless session (sem cookies, apenas JWT)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // OAuth2 Resource Server - valida JWT do Keycloak
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter)
                )
            )

            // Authorization rules - todos os outros endpoints (não públicos) requerem autenticação
            .authorizeHttpRequests(auth -> auth
                // Endpoints protegidos - exemplos de controle por role
                // (method-level security com @PreAuthorize é recomendado para controle fino)
                .requestMatchers(HttpMethod.GET, "/v1/locacoes/**").hasAnyRole("OPERADOR", "GERENTE", "ADMIN_TENANT")
                .requestMatchers(HttpMethod.POST, "/v1/locacoes/*/checkin").hasAnyRole("OPERADOR", "GERENTE", "ADMIN_TENANT")
                .requestMatchers(HttpMethod.POST, "/v1/locacoes/*/checkout").hasAnyRole("OPERADOR", "GERENTE", "ADMIN_TENANT")
                .requestMatchers(HttpMethod.POST, "/v1/fechamentos/**").hasAnyRole("GERENTE", "FINANCEIRO", "ADMIN_TENANT")

                // Todos os outros endpoints requerem autenticação
                .anyRequest().authenticated()
            )

            // TenantFilter DEPOIS do OAuth2 filter (para que JWT já esteja validado)
            // Valida que tenant_id do JWT == X-Tenant-Id header
            .addFilterAfter(tenantFilter(), org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuração CORS para permitir chamadas cross-origin.
     *
     * Permite:
     * - Origens configuradas por tenant (application.yml: jetski.security.allowed-origins)
     * - Métodos HTTP padrão
     * - Headers customizados (X-Tenant-Id, Authorization)
     * - Credentials (cookies) se necessário no futuro
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Origens permitidas (deve vir de configuração por tenant)
        configuration.setAllowedOriginPatterns(List.of(
            "http://localhost:3000",      // Frontend local (dev)
            "http://localhost:3001",      // Backoffice local (dev)
            "http://localhost:3002",      // Backoffice Next.js (dev)
            "https://*.jetski.app",       // Mobile app (produção)
            "https://*.jetski.com.br"     // Web app (produção)
        ));

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        configuration.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "X-Tenant-Id",     // Header obrigatório para multi-tenant
            "X-Request-Id"     // Para tracing
        ));

        configuration.setExposedHeaders(List.of(
            "X-Total-Count",   // Para paginação
            "X-Request-Id"
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Cache preflight por 1 hora

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
