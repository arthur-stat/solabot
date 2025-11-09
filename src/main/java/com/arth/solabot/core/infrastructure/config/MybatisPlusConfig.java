package com.arth.solabot.core.infrastructure.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class MybatisPlusConfig {

    private final DataSource dataSource;

    public MybatisPlusConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Bean
    public MybatisPlusInterceptor mpInterceptor() {
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        mybatisPlusInterceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return mybatisPlusInterceptor;
    }

    /**
     * 检查 SQL 数据库连接，列出所有表
     */
    @EventListener(ApplicationReadyEvent.class)
    public void checkSqlConnectionAndTables() {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            log.info("[sql] SQL database connection successful: {}", url);

            List<String> tables = getAllTables(conn);
            log.info("[sql] Found {} tables in database: {}", tables.size(), tables);

        } catch (SQLException e) {
            log.error("[sql] SQL database connection failed!", e);
        }
    }

    private List<String> getAllTables(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }
}