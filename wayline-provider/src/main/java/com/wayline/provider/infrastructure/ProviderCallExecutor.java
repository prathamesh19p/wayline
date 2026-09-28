package com.wayline.provider.infrastructure;

import com.wayline.provider.domain.ProviderException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs a provider call with a hard timeout.
 *
 * <p>Two details here are easy to get wrong and expensive when they are. Calls do not run on
 * {@code ForkJoinPool.commonPool}, which is sized for CPU-bound work and would be starved by
 * blocking HTTP. And {@link ExecutionException} is unwrapped before it reaches the caller, so a
 * {@code catch (ProviderException)} in calling code is actually reachable rather than dead.
 */
@Component
@Slf4j
public class ProviderCallExecutor {

    private final ExecutorService executor;
    private final long timeoutSeconds;

    public ProviderCallExecutor(
        @Value("${wayline.provider.timeout-seconds:10}") long timeoutSeconds,
        @Value("${wayline.provider.max-concurrent-calls:64}") int maxConcurrentCalls
    ) {
        this.timeoutSeconds = timeoutSeconds;
        this.executor = new ThreadPoolExecutor(
            4, maxConcurrentCalls, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(256),
            runnable -> {
                Thread thread = new Thread(runnable, "provider-call");
                thread.setDaemon(true);
                return thread;
            });
    }

    public <T> T call(Callable<T> providerCall) throws TimeoutException, InterruptedException {
        Future<T> future = executor.submit(providerCall);
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException exception) {
            // Cancel so a slow provider cannot pin a worker thread indefinitely.
            future.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ProviderException("Provider call failed: " + cause.getMessage(),
                "UNKNOWN", cause);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
