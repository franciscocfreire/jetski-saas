package com.jetski.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão do stress do portal (17/set/2026): com a fila do executor @Async cheia, o
 * AbortPolicy fazia o proxy lançar TaskRejectedException no request e a reserva virava 500.
 * Um efeito colateral (auditoria, métrica) nunca pode derrubar o negócio.
 */
class AsyncConfigTest {

    @Test
    void executorPadraoRodaNaThreadChamadoraQuandoAFilaEnche() throws Exception {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfig().getAsyncExecutor();
        try {
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);

            // Enche as threads e a fila com tarefas presas; a próxima NÃO pode ser rejeitada.
            CountDownLatch soltar = new CountDownLatch(1);
            AtomicInteger executadas = new AtomicInteger();
            int capacidade = executor.getMaxPoolSize() + executor.getQueueCapacity();
            for (int i = 0; i < capacidade; i++) {
                executor.execute(() -> {
                    try {
                        soltar.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            Thread chamadora = Thread.currentThread();
            Thread[] ondeRodou = new Thread[1];
            executor.execute(() -> {
                ondeRodou[0] = Thread.currentThread();
                executadas.incrementAndGet();
            });
            assertThat(executadas.get()).as("a tarefa excedente roda na hora, na thread chamadora").isEqualTo(1);
            assertThat(ondeRodou[0]).isSameAs(chamadora);
            soltar.countDown();
        } finally {
            executor.shutdown();
        }
    }
}
