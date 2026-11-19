package com.yiguan.his.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 业务单号生成器：前缀 + 时间戳 + 随机位，单机部署下足以保证唯一。
 */
public final class CodeGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private CodeGenerator() {
    }

    /** 如 P202611180001 之类的日期流水号。 */
    public static String dateCode(String prefix) {
        return prefix + LocalDate.now().format(DATE) + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    /** 如 V261118213012345 之类的时间戳单号，用于高频单据。 */
    public static String timeCode(String prefix) {
        return prefix + LocalDateTime.now().format(DATE_TIME)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(0, 1000));
    }
}
