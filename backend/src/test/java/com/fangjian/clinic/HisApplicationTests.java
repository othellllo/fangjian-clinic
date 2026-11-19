package com.fangjian.clinic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 上下文加载与 Flyway 迁移可用性检查。 */
@SpringBootTest
@ActiveProfiles("test")
class HisApplicationTests {

    @Test
    void contextLoads() {
        assertNotNull(HisApplication.class);
    }
}
