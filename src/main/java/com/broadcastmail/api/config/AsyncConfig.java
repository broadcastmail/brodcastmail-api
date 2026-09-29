package com.broadcastmail.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.server.ResponseStatusException;

@Configuration
public class AsyncConfig {
    @Bean(name = "sseTaskExecutor")
    public TaskExecutor sseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setThreadNamePrefix("sse-task-");
        executor.setRejectedExecutionHandler((runnable, exec) -> {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many active connections, please try again shortly");
        });

        executor.initialize();
        return executor;
    }
}
