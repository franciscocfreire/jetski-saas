package com.jetski.shared.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Configuração para propagar trace_id e span_id do OpenTelemetry para o MDC do Logback.
 * Isso permite correlacionar logs com traces no Jaeger/Grafana.
 */
@Configuration
@ConditionalOnClass(Tracer.class)
public class TracingMdcConfiguration {

    /**
     * Filter que adiciona trace_id e span_id ao MDC para cada request.
     * Executado DEPOIS do RequestCorrelationFilter (order 2).
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 3)
    public TracingMdcFilter tracingMdcFilter(Tracer tracer) {
        return new TracingMdcFilter(tracer);
    }

    /**
     * Filter que popula MDC com informações de tracing.
     */
    public static class TracingMdcFilter extends OncePerRequestFilter {

        private final Tracer tracer;

        public TracingMdcFilter(Tracer tracer) {
            this.tracer = tracer;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            Span currentSpan = tracer.currentSpan();

            if (currentSpan != null) {
                String traceId = currentSpan.context().traceId();
                String spanId = currentSpan.context().spanId();

                // Adiciona trace_id e span_id ao MDC
                MDC.put("trace_id", traceId);
                MDC.put("span_id", spanId);

                try {
                    filterChain.doFilter(request, response);
                } finally {
                    // Remove do MDC ao final do request
                    MDC.remove("trace_id");
                    MDC.remove("span_id");
                }
            } else {
                // Sem span ativo, continua sem tracing
                filterChain.doFilter(request, response);
            }
        }
    }
}
