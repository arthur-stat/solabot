package com.arth.solabot.core.infrastructure.config.dev;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.flyway", name = "clean-disabled", havingValue = "false")
public class DevDatabaseResetConfig {

    @Bean
    public FlywayMigrationStrategy cleanMigrateStrategy() {
        return flyway -> {
            log.warn("[dev] table rebuild starting, please confirm this is development mode.");
            flyway.clean();
            flyway.migrate();
            log.warn("[dev] all tables have been successfully initialized.");
        };
    }
}