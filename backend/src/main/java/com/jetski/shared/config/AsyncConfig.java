package com.jetski.shared.config;

import com.jetski.shared.security.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async configuration with TenantContext propagation.
 *
 * <p>This configuration ensures that the tenant context (stored in ThreadLocal)
 * is properly propagated to async threads. Without this, async methods would
 * lose the tenant context, causing RLS policies to fail.
 *
 * <p><strong>How it works:</strong>
 * <ol>
 *   <li>Before submitting a task, captures current TenantContext (tenantId, usuarioId)</li>
 *   <li>Wraps the task in a decorator that restores the context in the new thread</li>
 *   <li>After task completion, clears the context to prevent leaks</li>
 * </ol>
 *
 * <p><strong>Thread Pool Settings:</strong>
 * <ul>
 *   <li>Core pool: 2 threads (minimum always available)</li>
 *   <li>Max pool: 10 threads (scales up under load)</li>
 *   <li>Queue capacity: 500 (buffer before rejecting)</li>
 *   <li>Thread prefix: "async-audit-" (for easy identification in logs)</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.10.0
 * @see TenantContext
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Pool sizing - conservative for audit workload
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-audit-");

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // TaskDecorator to propagate TenantContext to async threads
        executor.setTaskDecorator(tenantAwareDecorator());

        executor.initialize();
        log.info("Async executor initialized with TenantContext propagation support");

        return executor;
    }


    /**
     * Propaga o TenantContext (ThreadLocal) para a thread assíncrona e limpa depois.
     * Sem isto, a RLS derruba as queries do worker; sem o clear, o tenant vaza para a
     * próxima tarefa da mesma thread de pool — com RLS, vazamento entre lojas.
     */
    private TaskDecorator tenantAwareDecorator() {
        return runnable -> {
            // Capture context from the calling thread
            UUID tenantId = TenantContext.getTenantId();
            UUID usuarioId = TenantContext.getUsuarioId();

            log.debug("Capturing context for async task: tenantId={}, usuarioId={}", tenantId, usuarioId);

            return () -> {
                try {
                    // Restore context in the async thread
                    if (tenantId != null) {
                        TenantContext.setTenantId(tenantId);
                    }
                    if (usuarioId != null) {
                        TenantContext.setUsuarioId(usuarioId);
                    }

                    log.debug("Restored context in async thread: tenantId={}, usuarioId={}",
                            TenantContext.getTenantId(), TenantContext.getUsuarioId());

                    // Execute the actual task
                    runnable.run();

                } finally {
                    // Always clear context to prevent leaks between tasks
                    TenantContext.clear();
                    log.debug("Cleared context after async task completion");
                }
            };
        };
    }

    /**
     * Pool próprio para o envio dos e-mails da emissão. Separado do pool de auditoria
     * porque cada envio segura a thread por ~12 s por anexo de 1 MB: com core 2
     * compartilhado, dois envios atrasariam as gravações da trilha.
     *
     * <p>{@code CallerRunsPolicy} em vez do {@code AbortPolicy} default: com a fila
     * cheia, abortar deixaria o documento PENDENTE órfão em silêncio; rodar na thread
     * chamadora degrada para o comportamento antigo (lento), mas o e-mail sai.
     */
    @Bean("envioDocumentosExecutor")
    public Executor envioDocumentosExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("envio-doc-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // O envio em curso precisa terminar antes do shutdown, senão o documento fica
        // PENDENTE e só sai por reenvio manual.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(90);
        executor.setTaskDecorator(tenantAwareDecorator());
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
