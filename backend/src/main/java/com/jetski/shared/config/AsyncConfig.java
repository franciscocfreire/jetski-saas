package com.jetski.shared.config;

import com.jetski.shared.security.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.UUID;
import java.util.concurrent.Executor;

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
        executor.setTaskDecorator(runnable -> {
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
        });

        executor.initialize();
        log.info("Async executor initialized with TenantContext propagation support");

        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
