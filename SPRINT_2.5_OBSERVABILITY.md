# Sprint 2.5 - Observabilidade Production-Ready

**Data:** 26 de Janeiro de 2025
**Versão:** 0.7.5-SNAPSHOT
**Objetivo:** Implementar stack completo de observabilidade antes do Sprint 3

---

## 1. CONTEXTO E MOTIVAÇÃO

### Problema Atual
O sistema possui apenas observabilidade básica:
- ✅ Spring Actuator com endpoints `/health`, `/metrics`, `/prometheus`
- ✅ Logs em texto simples no console
- ❌ Sem correlation ID (impossível rastrear request end-to-end)
- ❌ Sem logs estruturados (difícil fazer queries/agregação)
- ❌ Sem métricas customizadas de negócio
- ❌ Sem stack de monitoring (Prometheus, Grafana, Loki, Jaeger)
- ❌ Sem dashboards para visualizar comportamento
- ❌ Sem alertas para problemas

### Por Que Agora?
Implementar observabilidade **ANTES** do Sprint 3 (Fechamento Diário) porque:
1. **Debugging:** Visibilidade total ao desenvolver features complexas
2. **Performance:** Medir impacto de novas implementações
3. **Produção:** Preparar sistema para deploy real
4. **Confiança:** Saber exatamente o que está acontecendo

---

## 2. OBJETIVOS E ENTREGAS

### Objetivos
1. **Correlation ID**: Rastrear cada request do início ao fim
2. **Logs Estruturados**: JSON format com campos padronizados
3. **Distributed Tracing**: Ver latência por operação (HTTP → DB → OPA)
4. **Métricas Customizadas**: Medir operações de negócio (check-ins, reservas)
5. **Stack de Monitoring**: Prometheus + Grafana + Loki + Jaeger rodando local
6. **Dashboards**: 3 dashboards pré-configurados (System, Tenant, Business)
7. **Alertas**: Regras para detectar problemas automaticamente

### Não-Objetivos (Out of Scope)
- ❌ APM comercial (Datadog, New Relic) - usar stack open-source
- ❌ Deploy em AWS CloudWatch - focar em setup local primeiro
- ❌ Compliance LGPD (PII masking) - será Sprint 8
- ❌ Mobile app instrumentation - backend apenas

---

## 3. ARQUITETURA DE OBSERVABILIDADE

```
┌─────────────────────────────────────────────────────────────┐
│                    JETSKI BACKEND (Spring Boot)              │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ RequestCorrelationFilter                               │ │
│  │  - Gera trace_id (UUID)                                │ │
│  │  - Popula MDC (tenant_id, user_id, trace_id)          │ │
│  │  - Adiciona X-Trace-Id header                          │ │
│  └────────────────────────────────────────────────────────┘ │
│                          │                                   │
│                          ▼                                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ Logback com LogstashEncoder                            │ │
│  │  - Logs em JSON                                        │ │
│  │  - Inclui MDC automaticamente                          │ │
│  │  - Output: console + file                              │ │
│  └────────────────────────────────────────────────────────┘ │
│                          │                                   │
│                          ▼                                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ OpenTelemetry SDK                                      │ │
│  │  - Auto-instrumentação (HTTP, DB, Redis)               │ │
│  │  - Span creation                                       │ │
│  │  - W3C Trace Context propagation                       │ │
│  └────────────────────────────────────────────────────────┘ │
│                          │                                   │
│                          ▼                                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ Micrometer Metrics                                     │ │
│  │  - @Timed annotations (duração)                        │ │
│  │  - @Counted annotations (contadores)                   │ │
│  │  - Custom meters (Gauge, Timer, Counter)               │ │
│  │  - Tenant tags automáticos                             │ │
│  └────────────────────────────────────────────────────────┘ │
│                          │                                   │
│                          ▼                                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ Actuator Endpoints                                     │ │
│  │  - /actuator/health (readiness, liveness)              │ │
│  │  - /actuator/prometheus (metrics export)               │ │
│  │  - /actuator/info                                      │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                │                │                │
                │ (logs)         │ (metrics)      │ (traces)
                ▼                ▼                ▼
         ┌───────────┐    ┌────────────┐   ┌──────────┐
         │ Promtail  │    │ Prometheus │   │  Jaeger  │
         │   :9080   │    │   :9090    │   │  :16686  │
         └───────────┘    └────────────┘   └──────────┘
                │                │                │
                ▼                │                │
         ┌───────────┐           │                │
         │   Loki    │           │                │
         │   :3100   │           │                │
         └───────────┘           │                │
                │                │                │
                └────────────────┴────────────────┘
                                 │
                                 ▼
                         ┌──────────────┐
                         │   Grafana    │
                         │    :3000     │
                         │              │
                         │ Dashboards:  │
                         │ - System     │
                         │ - Tenant     │
                         │ - Business   │
                         └──────────────┘
```

---

## 4. IMPLEMENTAÇÃO DETALHADA

### 4.1 Correlation ID + MDC

#### RequestCorrelationFilter.java
```java
package com.jetski.shared.observability;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Request Correlation Filter
 *
 * Generates a unique trace_id for each HTTP request and populates MDC
 * (Mapped Diagnostic Context) with contextual information.
 *
 * MDC keys populated:
 * - trace_id: Unique identifier for this request
 * - tenant_id: Extracted from X-Tenant-Id header
 * - remote_ip: Client IP (considering X-Forwarded-For)
 *
 * The trace_id is also added to the response header as X-Trace-Id.
 */
@Component
@Order(1)  // Execute before TenantFilter
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        // Generate or extract trace_id
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }

        try {
            // Populate MDC with contextual information
            MDC.put(MDCKeys.TRACE_ID, traceId);
            MDC.put(MDCKeys.TENANT_ID, request.getHeader(TENANT_ID_HEADER));
            MDC.put(MDCKeys.REMOTE_IP, extractClientIP(request));
            MDC.put(MDCKeys.REQUEST_METHOD, request.getMethod());
            MDC.put(MDCKeys.REQUEST_URI, request.getRequestURI());

            // Add trace_id to response header for client correlation
            response.addHeader(TRACE_ID_HEADER, traceId);

            // Continue filter chain
            filterChain.doFilter(request, response);
        } finally {
            // Always clear MDC to prevent memory leaks
            MDC.clear();
        }
    }

    /**
     * Extract client IP considering X-Forwarded-For header (for proxies/load balancers)
     */
    private String extractClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can be: client, proxy1, proxy2
            // We want the first (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Filter all requests (no exceptions)
        return false;
    }
}
```

#### MDCKeys.java
```java
package com.jetski.shared.observability;

/**
 * MDC (Mapped Diagnostic Context) Keys
 *
 * Centralized constants for all MDC keys used across the application.
 * These keys are automatically included in all log entries when using
 * Logback with LogstashEncoder.
 */
public final class MDCKeys {

    private MDCKeys() {
        // Utility class, no instantiation
    }

    /**
     * Unique identifier for the current HTTP request
     * Format: UUID (e.g., "abc123-def456-ghi789")
     */
    public static final String TRACE_ID = "trace_id";

    /**
     * Tenant identifier extracted from X-Tenant-Id header
     * Format: UUID
     */
    public static final String TENANT_ID = "tenant_id";

    /**
     * User identifier (populated after authentication)
     * Format: UUID
     */
    public static final String USER_ID = "user_id";

    /**
     * Client IP address (considering X-Forwarded-For)
     * Format: IPv4 or IPv6 address
     */
    public static final String REMOTE_IP = "remote_ip";

    /**
     * HTTP method (GET, POST, PUT, DELETE, etc.)
     */
    public static final String REQUEST_METHOD = "request_method";

    /**
     * Request URI path
     */
    public static final String REQUEST_URI = "request_uri";
}
```

### 4.2 Logs Estruturados (JSON)

#### logback-spring.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <!-- Import Spring Boot's default configuration -->
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- Property: Application name -->
    <springProperty scope="context" name="appName" source="spring.application.name" defaultValue="jetski-api"/>

    <!-- Property: Active profile -->
    <springProperty scope="context" name="activeProfile" source="spring.profiles.active" defaultValue="local"/>

    <!-- ================================================================ -->
    <!-- APPENDERS -->
    <!-- ================================================================ -->

    <!-- Console Appender with JSON format (for local/dev) -->
    <appender name="CONSOLE_JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"app":"${appName}","environment":"${activeProfile}"}</customFields>
            <includeMdcKeyName>trace_id</includeMdcKeyName>
            <includeMdcKeyName>tenant_id</includeMdcKeyName>
            <includeMdcKeyName>user_id</includeMdcKeyName>
            <includeMdcKeyName>remote_ip</includeMdcKeyName>
            <includeMdcKeyName>request_method</includeMdcKeyName>
            <includeMdcKeyName>request_uri</includeMdcKeyName>
            <timestampPattern>yyyy-MM-dd'T'HH:mm:ss.SSS'Z'</timestampPattern>
        </encoder>
    </appender>

    <!-- Console Appender with plain text (for local development) -->
    <appender name="CONSOLE_TEXT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %clr([%X{trace_id:-no-trace}]){cyan} %clr([%thread]){magenta} %clr(%-5level) %clr(%logger{36}){blue} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- File Appender with JSON format (for production) -->
    <appender name="FILE_JSON" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/jetski-api.log</file>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"app":"${appName}","environment":"${activeProfile}"}</customFields>
            <includeMdcKeyName>trace_id</includeMdcKeyName>
            <includeMdcKeyName>tenant_id</includeMdcKeyName>
            <includeMdcKeyName>user_id</includeMdcKeyName>
            <includeMdcKeyName>remote_ip</includeMdcKeyName>
            <timestampPattern>yyyy-MM-dd'T'HH:mm:ss.SSS'Z'</timestampPattern>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/jetski-api-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxHistory>30</maxHistory>
            <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>100MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
        </rollingPolicy>
    </appender>

    <!-- ================================================================ -->
    <!-- PROFILE-SPECIFIC CONFIGURATIONS -->
    <!-- ================================================================ -->

    <!-- LOCAL PROFILE: Plain text console for readability -->
    <springProfile name="local">
        <root level="INFO">
            <appender-ref ref="CONSOLE_TEXT"/>
        </root>
        <logger name="com.jetski" level="DEBUG"/>
        <logger name="org.springframework.security" level="DEBUG"/>
    </springProfile>

    <!-- DEV PROFILE: JSON console for testing structured logs -->
    <springProfile name="dev">
        <root level="INFO">
            <appender-ref ref="CONSOLE_JSON"/>
        </root>
        <logger name="com.jetski" level="DEBUG"/>
        <logger name="org.springframework.security" level="DEBUG"/>
        <logger name="org.hibernate.SQL" level="DEBUG"/>
    </springProfile>

    <!-- TEST PROFILE: Quiet logging for tests -->
    <springProfile name="test">
        <root level="WARN">
            <appender-ref ref="CONSOLE_TEXT"/>
        </root>
        <logger name="com.jetski" level="INFO"/>
    </springProfile>

    <!-- PROD PROFILE: JSON file + console -->
    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="CONSOLE_JSON"/>
            <appender-ref ref="FILE_JSON"/>
        </root>
        <logger name="com.jetski" level="INFO"/>
        <logger name="org.springframework.security" level="INFO"/>
    </springProfile>

</configuration>
```

### 4.3 OpenTelemetry Configuration

#### TracingConfiguration.java
```java
package com.jetski.shared.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry Configuration
 *
 * Configures distributed tracing with OpenTelemetry SDK.
 * Exports traces to Jaeger via OTLP (OpenTelemetry Protocol).
 *
 * Sampling strategy:
 * - local/dev: 100% (trace everything)
 * - prod: 10% (sample 1 in 10 requests)
 */
@Configuration
public class TracingConfiguration {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${observability.tracing.endpoint:http://localhost:4317}")
    private String otlpEndpoint;

    @Value("${observability.tracing.sampling-probability:1.0}")
    private double samplingProbability;

    @Bean
    public OpenTelemetry openTelemetry() {
        // Resource with service name
        Resource resource = Resource.create(
            Attributes.builder()
                .put(ResourceAttributes.SERVICE_NAME, applicationName)
                .put(ResourceAttributes.SERVICE_VERSION, "0.7.5-SNAPSHOT")
                .build()
        );

        // OTLP exporter (sends traces to Jaeger)
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(otlpEndpoint)
            .build();

        // Tracer provider with sampling
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setSampler(Sampler.traceIdRatioBased(samplingProbability))
            .build();

        // Build OpenTelemetry SDK
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(applicationName, "0.7.5");
    }
}
```

### 4.4 Métricas Customizadas

#### MetricsConfiguration.java
```java
package com.jetski.shared.observability;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.Arrays;

/**
 * Metrics Configuration
 *
 * Configures Micrometer for custom metrics:
 * - Enable @Timed aspect for automatic timing
 * - Add global tags to all metrics (application, environment)
 * - Configure percentiles for latency distribution
 */
@Configuration
@EnableAspectJAutoProxy
public class MetricsConfiguration {

    /**
     * Enable @Timed annotation support
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * Customize meter registry with global tags
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags(Tags.of(
                Tag.of("application", "jetski-api"),
                Tag.of("environment", System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "local"))
            ));
    }
}
```

#### TenantMetricsAspect.java
```java
package com.jetski.shared.observability;

import com.jetski.shared.internal.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tenant Metrics Aspect
 *
 * Automatically adds tenant_id tag to all @Timed and @Counted metrics.
 * This allows filtering metrics by tenant in Grafana dashboards.
 *
 * Also populates MDC with user_id from SecurityContext.
 */
@Aspect
@Component
public class TenantMetricsAspect {

    private final MeterRegistry meterRegistry;

    public TenantMetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Add tenant_id to MDC for all requests inside tenant context
     */
    @Around("execution(* com.jetski..*Controller.*(..))")
    public Object addTenantContext(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID tenantId = TenantContext.getTenantId();

        if (tenantId != null) {
            // Populate MDC with tenant_id (if not already set by RequestCorrelationFilter)
            if (MDC.get(MDCKeys.TENANT_ID) == null) {
                MDC.put(MDCKeys.TENANT_ID, tenantId.toString());
            }
        }

        return joinPoint.proceed();
    }

    /**
     * Helper to create tags with tenant_id
     */
    public static Tags tenantTags(UUID tenantId) {
        if (tenantId == null) {
            return Tags.of(Tag.of("tenant_id", "none"));
        }
        return Tags.of(Tag.of("tenant_id", tenantId.toString()));
    }

    /**
     * Helper to create tags with tenant_id and status
     */
    public static Tags tenantTags(UUID tenantId, String status) {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("tenant_id", tenantId != null ? tenantId.toString() : "none"));
        tags.add(Tag.of("status", status));
        return Tags.of(tags);
    }
}
```

---

## 5. DOCKER COMPOSE - OBSERVABILITY STACK

### docker-compose-observability.yml
```yaml
version: '3.8'

services:
  # ================================================================
  # PROMETHEUS - Metrics Collection
  # ================================================================
  prometheus:
    image: prom/prometheus:v2.48.0
    container_name: jetski-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - ./prometheus/alerting-rules.yml:/etc/prometheus/alerting-rules.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'
      - '--web.enable-lifecycle'
    networks:
      - jetski-network
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:9090/-/healthy"]
      interval: 10s
      timeout: 5s
      retries: 3

  # ================================================================
  # GRAFANA - Visualization
  # ================================================================
  grafana:
    image: grafana/grafana:10.2.2
    container_name: jetski-grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
      - GF_SERVER_ROOT_URL=http://localhost:3000
      - GF_LOG_LEVEL=info
    volumes:
      - ./grafana/datasources:/etc/grafana/provisioning/datasources
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards
      - grafana-data:/var/lib/grafana
    networks:
      - jetski-network
    depends_on:
      - prometheus
      - loki
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:3000/api/health"]
      interval: 10s
      timeout: 5s
      retries: 3

  # ================================================================
  # LOKI - Log Aggregation
  # ================================================================
  loki:
    image: grafana/loki:2.9.3
    container_name: jetski-loki
    ports:
      - "3100:3100"
    volumes:
      - ./loki/loki-config.yml:/etc/loki/local-config.yaml
      - loki-data:/loki
    command: -config.file=/etc/loki/local-config.yaml
    networks:
      - jetski-network
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:3100/ready"]
      interval: 10s
      timeout: 5s
      retries: 3

  # ================================================================
  # PROMTAIL - Log Collector
  # ================================================================
  promtail:
    image: grafana/promtail:2.9.3
    container_name: jetski-promtail
    volumes:
      - ./promtail/promtail-config.yml:/etc/promtail/config.yml
      - ../backend/logs:/var/log/jetski
    command: -config.file=/etc/promtail/config.yml
    networks:
      - jetski-network
    depends_on:
      - loki

  # ================================================================
  # JAEGER - Distributed Tracing
  # ================================================================
  jaeger:
    image: jaegertracing/all-in-one:1.51
    container_name: jetski-jaeger
    ports:
      - "5775:5775/udp"  # Compact thrift protocol (deprecated)
      - "6831:6831/udp"  # Compact thrift protocol
      - "6832:6832/udp"  # Binary thrift protocol
      - "5778:5778"      # Serve configs
      - "16686:16686"    # Jaeger UI
      - "14268:14268"    # Accept jaeger.thrift
      - "14250:14250"    # Accept model.proto
      - "9411:9411"      # Zipkin compatible endpoint
      - "4317:4317"      # OTLP gRPC
      - "4318:4318"      # OTLP HTTP
    environment:
      - COLLECTOR_ZIPKIN_HOST_PORT=:9411
      - COLLECTOR_OTLP_ENABLED=true
    networks:
      - jetski-network
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:14269/"]
      interval: 10s
      timeout: 5s
      retries: 3

volumes:
  prometheus-data:
  grafana-data:
  loki-data:

networks:
  jetski-network:
    external: true
```

---

## 6. PROMETHEUS CONFIGURATION

### prometheus/prometheus.yml
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    cluster: 'jetski-local'
    environment: 'dev'

# Alerting configuration
alerting:
  alertmanagers:
    - static_configs:
        - targets: []
          # - 'alertmanager:9093'  # Uncomment when deploying AlertManager

# Load alerting rules
rule_files:
  - '/etc/prometheus/alerting-rules.yml'

# Scrape configurations
scrape_configs:
  # Jetski Backend API
  - job_name: 'jetski-api'
    metrics_path: '/api/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8090']
        labels:
          service: 'jetski-api'
          environment: 'local'

  # Prometheus itself
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  # PostgreSQL exporter (future)
  # - job_name: 'postgres'
  #   static_configs:
  #     - targets: ['postgres-exporter:9187']

  # Redis exporter (future)
  # - job_name: 'redis'
  #   static_configs:
  #     - targets: ['redis-exporter:9121']

  # Keycloak metrics
  - job_name: 'keycloak'
    metrics_path: '/metrics'
    static_configs:
      - targets: ['host.docker.internal:8081']
        labels:
          service: 'keycloak'
```

### prometheus/alerting-rules.yml
```yaml
groups:
  - name: jetski_api_alerts
    interval: 30s
    rules:
      # High error rate (>5% in 5 minutes)
      - alert: HighErrorRate
        expr: |
          (
            sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
            /
            sum(rate(http_server_requests_seconds_count[5m]))
          ) > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value | humanizePercentage }} (threshold: 5%)"

      # High latency (P95 > 300ms)
      - alert: HighLatency
        expr: |
          histogram_quantile(0.95,
            sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri)
          ) > 0.3
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High latency detected on {{ $labels.uri }}"
          description: "P95 latency is {{ $value | humanizeDuration }} (threshold: 300ms)"

      # Database connection pool exhausted
      - alert: DatabaseConnectionExhausted
        expr: |
          (
            hikaricp_connections_active / hikaricp_connections_max
          ) > 0.9
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Database connection pool nearly exhausted"
          description: "{{ $value | humanizePercentage }} of connections in use (threshold: 90%)"

      # Keycloak down
      - alert: KeycloakDown
        expr: up{job="keycloak"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Keycloak is down"
          description: "Keycloak service is not responding"

      # High JVM memory usage
      - alert: HighJVMMemoryUsage
        expr: |
          (
            jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}
          ) > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High JVM heap memory usage"
          description: "JVM heap is {{ $value | humanizePercentage }} full (threshold: 90%)"
```

---

## 7. GRAFANA DASHBOARDS

### grafana/datasources/prometheus.yml
```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
    jsonData:
      timeInterval: "15s"
```

### grafana/datasources/loki.yml
```yaml
apiVersion: 1

datasources:
  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    editable: true
    jsonData:
      maxLines: 1000
```

### grafana/dashboards/dashboard-config.yml
```yaml
apiVersion: 1

providers:
  - name: 'default'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /etc/grafana/provisioning/dashboards
      foldersFromFilesStructure: true
```

---

## 8. LOKI & PROMTAIL CONFIG

### loki/loki-config.yml
```yaml
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096

common:
  instance_addr: 127.0.0.1
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2020-10-24
      store: boltdb-shipper
      object_store: filesystem
      schema: v11
      index:
        prefix: index_
        period: 24h

ruler:
  alertmanager_url: http://localhost:9093

# By default, Loki will send anonymous, but uniquely-identifiable usage and configuration
# analytics to Grafana Labs. These statistics are sent to https://stats.grafana.org/
#
# Statistics help us better understand how Loki is used, and they show us performance
# levels for most users. This helps us prioritize features and documentation.
# For more information on what's sent, look at
# https://github.com/grafana/loki/blob/main/pkg/usagestats/stats.go
# Refer to the buildReport method to see what goes into a report.
#
# If you would like to disable reporting, uncomment the following lines:
#analytics:
#  reporting_enabled: false
```

### promtail/promtail-config.yml
```yaml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  # Jetski API logs (JSON format)
  - job_name: jetski-api
    static_configs:
      - targets:
          - localhost
        labels:
          job: jetski-api
          __path__: /var/log/jetski/*.log
    pipeline_stages:
      # Parse JSON logs
      - json:
          expressions:
            timestamp: timestamp
            level: level
            logger: logger
            message: message
            trace_id: trace_id
            tenant_id: tenant_id
            user_id: user_id
            remote_ip: remote_ip

      # Extract labels
      - labels:
          level:
          trace_id:
          tenant_id:

      # Set timestamp
      - timestamp:
          source: timestamp
          format: RFC3339
```

---

## 9. DEPENDÊNCIAS (pom.xml)

```xml
<!-- Logstash JSON Encoder -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>

<!-- OpenTelemetry Spring Boot Starter -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
    <version>2.0.0</version>
</dependency>

<!-- Micrometer Prometheus Registry -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- Micrometer Tracing Bridge for OpenTelemetry -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<!-- OpenTelemetry OTLP Exporter -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

---

## 10. APPLICATION.YML UPDATES

```yaml
# Observability Configuration
observability:
  tracing:
    endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
    sampling-probability: ${OTEL_TRACE_SAMPLING_PROBABILITY:1.0}  # 100% local, 10% prod

# Management endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,loggers
      base-path: /api/actuator
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true  # Enable /health/readiness and /health/liveness
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true  # Enable P50, P95, P99
  tracing:
    sampling:
      probability: ${observability.tracing.sampling-probability}

# Logging
logging:
  level:
    root: INFO
    com.jetski: DEBUG
    org.springframework.security: INFO
    io.opentelemetry: INFO
  file:
    name: logs/jetski-api.log
    max-size: 100MB
    max-history: 30
```

---

## 11. SCRIPTS DE AUTOMAÇÃO

### infra/start-observability-stack.sh
```bash
#!/bin/bash

set -e

echo "🚀 Starting Jetski Observability Stack..."

# Create logs directory
mkdir -p ../backend/logs

# Create network if doesn't exist
docker network inspect jetski-network >/dev/null 2>&1 || \
    docker network create jetski-network

# Start observability services
docker-compose -f docker-compose-observability.yml up -d

echo ""
echo "⏳ Waiting for services to be healthy..."

# Wait for Prometheus
until curl -f http://localhost:9090/-/healthy >/dev/null 2>&1; do
    echo "  ⏳ Waiting for Prometheus..."
    sleep 2
done
echo "  ✅ Prometheus is healthy (http://localhost:9090)"

# Wait for Loki
until curl -f http://localhost:3100/ready >/dev/null 2>&1; do
    echo "  ⏳ Waiting for Loki..."
    sleep 2
done
echo "  ✅ Loki is ready (http://localhost:3100)"

# Wait for Jaeger
until curl -f http://localhost:14269/ >/dev/null 2>&1; do
    echo "  ⏳ Waiting for Jaeger..."
    sleep 2
done
echo "  ✅ Jaeger is ready (http://localhost:16686)"

# Wait for Grafana
until curl -f http://localhost:3000/api/health >/dev/null 2>&1; do
    echo "  ⏳ Waiting for Grafana..."
    sleep 2
done
echo "  ✅ Grafana is ready (http://localhost:3000)"

echo ""
echo "✅ Observability stack is UP!"
echo ""
echo "📊 Access points:"
echo "  - Grafana:    http://localhost:3000 (admin/admin)"
echo "  - Prometheus: http://localhost:9090"
echo "  - Jaeger UI:  http://localhost:16686"
echo "  - Loki:       http://localhost:3100"
echo ""
echo "🎯 Next steps:"
echo "  1. Start Jetski backend: cd backend && mvn spring-boot:run"
echo "  2. Make some requests to generate data"
echo "  3. Open Grafana dashboards"
echo ""
```

---

## 12. TESTES DE ACEITAÇÃO

### Teste 1: Correlation ID
```bash
# Make request
curl -v http://localhost:8090/api/v1/tenants/{id}/locacoes \
  -H "X-Tenant-Id: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11" \
  -H "Authorization: Bearer $TOKEN"

# Expected: Response header X-Trace-Id present
# Example: X-Trace-Id: abc123-def456-ghi789
```

### Teste 2: Logs Estruturados
```bash
# Check logs
tail -f backend/logs/jetski-api.log | jq .

# Expected: JSON format
{
  "timestamp": "2025-01-26T10:30:00.123Z",
  "level": "INFO",
  "logger": "com.jetski.locacoes.api.LocacaoController",
  "message": "Check-out request received",
  "trace_id": "abc123-def456",
  "tenant_id": "a0eebc99...",
  "user_id": "user-uuid",
  "app": "jetski-api",
  "environment": "dev"
}
```

### Teste 3: Distributed Tracing
```bash
# 1. Make request to backend
curl http://localhost:8090/api/v1/tenants/{id}/reservas

# 2. Open Jaeger UI
open http://localhost:16686

# 3. Search for traces:
#    - Service: jetski-api
#    - Operation: GET /api/v1/tenants/{id}/reservas
#    - Lookback: Last 1 hour

# Expected: See trace with spans:
#   - HTTP GET request
#   - Database query (SELECT from reserva)
#   - OPA authorization call
```

### Teste 4: Métricas Customizadas
```bash
# Open Prometheus
open http://localhost:9090

# Run queries:
# 1. Check-out duration
checkout_duration_seconds{tenant_id="a0eebc99..."}

# 2. Check-out count
checkout_total{tenant_id="a0eebc99...", status="success"}

# 3. HTTP request rate
rate(http_server_requests_seconds_count[5m])

# Expected: Data present in all queries
```

### Teste 5: Grafana Dashboards
```bash
# 1. Open Grafana
open http://localhost:3000

# 2. Login: admin/admin

# 3. Navigate to Dashboards:
#    - System Health Dashboard
#    - Tenant Metrics Dashboard
#    - Business KPIs Dashboard

# Expected: All 3 dashboards loaded with data
```

### Teste 6: Alertas
```bash
# Simulate high error rate (generate 50 errors)
for i in {1..50}; do
  curl -X GET http://localhost:8090/api/v1/nonexistent
done

# Check Prometheus Alerts
open http://localhost:9090/alerts

# Expected: HighErrorRate alert FIRING after 5 minutes
```

---

## 13. TROUBLESHOOTING

### Logs não aparecem no Loki
```bash
# Check Promtail status
docker logs jetski-promtail

# Check Loki health
curl http://localhost:3100/ready

# Verify log file exists
ls -lh backend/logs/jetski-api.log

# Test Loki query manually
curl -G -s "http://localhost:3100/loki/api/v1/query" \
  --data-urlencode 'query={job="jetski-api"}' | jq .
```

### Traces não aparecem no Jaeger
```bash
# Check OTLP endpoint
curl http://localhost:4317

# Verify OpenTelemetry SDK initialized
# Look for log: "OpenTelemetry SDK initialized"

# Check Jaeger UI
open http://localhost:16686
# Service: jetski-api should be listed
```

### Métricas não aparecem no Prometheus
```bash
# Check Prometheus targets
open http://localhost:9090/targets

# jetski-api should be UP

# If DOWN, check backend /actuator/prometheus
curl http://localhost:8090/api/actuator/prometheus

# Should return Prometheus text format
```

### Grafana dashboards vazios
```bash
# Check datasource connection
curl -u admin:admin http://localhost:3000/api/datasources

# Test Prometheus datasource
curl -u admin:admin \
  http://localhost:3000/api/datasources/proxy/1/api/v1/query?query=up

# Should return data
```

---

## 14. PRÓXIMOS PASSOS

Após Sprint 2.5 completo:
- ✅ Correlation ID funcionando
- ✅ Logs estruturados em JSON
- ✅ Distributed tracing com Jaeger
- ✅ Métricas customizadas instrumentadas
- ✅ Stack de monitoring rodando (Prometheus, Grafana, Loki)
- ✅ Dashboards pré-configurados
- ✅ Alertas básicos configurados

**Próximo Sprint:**
- Sprint 3: Fechamento Diário Operacional (com total visibilidade!)

---

**Fim da Especificação Sprint 2.5**
