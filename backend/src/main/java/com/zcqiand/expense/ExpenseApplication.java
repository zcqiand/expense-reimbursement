package com.zcqiand.expense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 财务报销系统 Spring Boot 入口。
 *
 * 《Harness 工程》第 11 章「项目规划与架构设计」的实物：
 * - 单体应用（不是微服务）：见第 11.5 节决策
 * - 入口类放包根 com.zcqiand.expense，让 @ComponentScan 默认就能扫到
 *   controller/service/repository/entity 四个子包
 *
 * 启动方式：
 *   开发：mvn spring-boot:run
 *   容器：docker compose up backend
 *   测试：mvn test
 *
 * 健康检查 endpoint：GET /actuator/health
 * API 文档 endpoint：GET /swagger-ui.html
 */
@SpringBootApplication
public class ExpenseApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseApplication.class, args);
    }
}
