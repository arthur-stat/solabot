package com.arth.solabot.core.general.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.TimeZone;

@Configuration
public class SchedulerConfig {

    public final TimeZone timeZone;

    @Autowired
    public SchedulerConfig(@Value("${app.time-zone}") String timeZoneStr) {
        timeZone = TimeZone.getTimeZone(timeZoneStr);
    }

    @Bean
    public TaskScheduler taskScheduler(@Value("${spring.task-scheduler-pool-size}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.initialize();
        return scheduler;
    }
}