package com.jetski.tenant.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.ModuloPlano;
import com.jetski.tenant.PlanoLimiteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gating de API dos módulos por plano (V046) — o par do gating de menu:
 * esconder item no frontend não é enforcement. Requests tenant-scoped a
 * paths de módulo fora do plano recebem negação de negócio (400) com
 * mensagem de upgrade.
 *
 * <p>Superadmin (unrestricted) e paths não-tenant passam direto. Lê os
 * módulos via {@link PlanoLimiteService#modulosDoPlano} (cache Redis;
 * evict na troca de plano/módulos).
 */
@Component
@RequiredArgsConstructor
public class ModuloPlanoInterceptor implements HandlerInterceptor {

    private static final Pattern TENANT_PATH =
        Pattern.compile("^/v1/tenants/[0-9a-fA-F-]{36}/(.+)$");

    private final PlanoLimiteService planoLimiteService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null || TenantContext.isUnrestricted()) {
            return true;
        }
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        Matcher m = TENANT_PATH.matcher(uri);
        if (!m.matches()) {
            return true;
        }
        String subPath = m.group(1);
        // Um path pode ser coberto por MAIS de um módulo (ex.: documentos/grus é
        // EMISSAO_PROPRIA e EMISSAO_DELEGADA): basta UM deles no plano para liberar.
        List<ModuloPlano> cobridores = java.util.Arrays.stream(ModuloPlano.values())
            .filter(mod -> mod.cobre(subPath))
            .toList();
        if (cobridores.isEmpty()) {
            return true;
        }
        List<String> modulos = planoLimiteService.modulosDoPlano(tenantId);
        boolean liberado = modulos.contains("*")
            || cobridores.stream().anyMatch(mod -> modulos.contains(mod.name()));
        if (!liberado) {
            String rotulos = cobridores.stream()
                .map(mod -> "\"" + mod.rotulo() + "\"")
                .collect(java.util.stream.Collectors.joining(" ou "));
            throw new BusinessException(
                "O módulo " + rotulos + " não está incluído no seu plano. "
                + "Faça upgrade em Plano e Faturas para habilitá-lo.");
        }
        return true;
    }

    /** Registra o interceptor nos paths tenant-scoped. */
    @Configuration
    @RequiredArgsConstructor
    static class Config implements WebMvcConfigurer {
        private final ModuloPlanoInterceptor interceptor;

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            // order > 0: roda DEPOIS do ABACAuthorizationInterceptor — a negação de
            // autorização (403) não pode ser mascarada pela negação de módulo (400).
            registry.addInterceptor(interceptor).order(10).addPathPatterns("/v1/tenants/**");
        }
    }
}
