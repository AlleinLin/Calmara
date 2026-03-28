package com.calmara.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@ComponentScan(basePackages = {
        "com.calmara.common",
        "com.calmara.model",
        "com.calmara.multimodal",
        "com.calmara.agent",
        "com.calmara.mcp",
        "com.calmara.security",
        "com.calmara.api",
        "com.calmara.admin",
        "com.calmara.web"
})
@MapperScan("com.calmara.model.mapper")
public class CalmaraApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalmaraApplication.class, args);
    }
}
