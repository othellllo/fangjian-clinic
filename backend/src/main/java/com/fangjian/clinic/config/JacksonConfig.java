package com.fangjian.clinic.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一 java.time 的 JSON 格式，与前端契约一致：
 * 日期时间 yyyy-MM-dd HH:mm:ss，日期 yyyy-MM-dd。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer javaTimeCustomizer() {
        return builder -> builder
                .serializers(new LocalDateTimeSerializer(DATE_TIME), new LocalDateSerializer(DATE))
                .deserializers(new LocalDateTimeDeserializer(DATE_TIME), new LocalDateDeserializer(DATE));
    }
}
