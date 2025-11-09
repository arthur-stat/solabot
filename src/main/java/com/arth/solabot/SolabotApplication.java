package com.arth.solabot;

import com.arth.solabot.core.infrastructure.database.mapper.MapperPackageMarker;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(basePackageClasses = MapperPackageMarker.class)
public class SolabotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SolabotApplication.class, args);
    }

}
