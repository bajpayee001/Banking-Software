package com.bank.transaction.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableAsync
public class AsyncThreadPoolConfig {

    @Value("${banking.concurrency.core-pool-size:20}")
    private int corePoolSize;

    @Value("${banking.concurrency.max-pool-size:40}")
    private int maxPoolSize;

    @Value("${banking.concurrency.queue-capacity:60000}")
    private int queueCapacity;

    @Value("${banking.concurrency.thread-name-prefix:BankTxnWorker-}")
    private String threadNamePrefix;

    @Bean(name = "transactionExecutor")
    public ExecutorService transactionExecutor() {
        AtomicInteger threadCount = new AtomicInteger(1);
        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r);
            thread.setName(threadNamePrefix + threadCount.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };

        // Bounded thread pool with bounded BlockingQueue
        return new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
