package com.fangjian.clinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * 方笺 启动类。
 *
 * <p>排除默认的 UserDetailsService 自动配置：鉴权完全由 JWT 过滤器完成，
 * 不需要 Spring Security 生成内存用户与随机密码。</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class HisApplication {

    public static void main(String[] args) {
        SpringApplication.run(HisApplication.class, args);
    }
}
