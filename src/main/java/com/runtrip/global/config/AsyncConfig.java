package com.runtrip.global.config;

import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig {

	// 추천 이유(/reasons) 백그라운드 호출. 큐가 차면 버리고, 그 코스는 PENDING으로 남아 앱이 개요를 보여준다
	@Bean
	public ThreadPoolTaskExecutor reasonExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("reason-");
		executor.setRejectedExecutionHandler((task, pool) -> log.warn("추천 이유 작업 큐가 가득 차서 버림"));
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(10);
		return executor;
	}
}
