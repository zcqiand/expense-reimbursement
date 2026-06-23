package com.zcqiand.expense;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 最小冒烟测试：Spring 上下文能正常启动。
 *
 * 用 H2 内嵌数据库代替真实 PostgreSQL——避免 mvn test 依赖外部容器。
 * 第 15 章会演示完整的 Testcontainers + 真实 PostgreSQL 集成测试。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:expense;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
class ExpenseApplicationTests {

    @Test
    void contextLoads() {
        // Spring 容器能启动 = 所有 @Component / @Bean 配置无环、无空引用、无类型冲突
    }
}
