package com.yiguan.his.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * 便携版本地部署：每次启动先把数据库整库备份成 zip，再清理过期备份。
 *
 * <p>医馆的数据丢了补不回来，而单机部署最容易发生的就是误删程序目录、
 * 硬盘损坏、升级失败。默认保留最近 7 份，备份文件在程序目录 {@code backup/} 下。</p>
 *
 * <p>备份失败只告警，不阻塞启动。</p>
 */
@Component
@ConditionalOnProperty(name = "app.backup.enabled", havingValue = "true")
@Order(100)
public class AutoBackupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AutoBackupRunner.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.backup.dir:./backup}")
    private String backupDir;

    @Value("${app.backup.keep:7}")
    private int keep;

    public AutoBackupRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            Path dir = Paths.get(backupDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve("yiguan-" + LocalDateTime.now().format(STAMP) + ".zip");
            String sqlPath = target.toString().replace('\\', '/');
            jdbcTemplate.execute("BACKUP TO '" + sqlPath + "'");
            log.info("已自动备份数据库：{}", target);
            prune(dir);
        } catch (Exception ex) {
            log.warn("自动备份失败（不影响系统启动）：{}", ex.getMessage());
        }
    }

    private void prune(Path dir) throws IOException {
        int keepCount = Math.max(keep, 1);
        try (var stream = Files.list(dir)) {
            List<Path> files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparingLong(path -> path.toFile().lastModified()))
                    .toList();
            int extra = files.size() - keepCount;
            for (int i = 0; i < extra; i++) {
                Files.deleteIfExists(files.get(i));
                log.info("已删除过期备份：{}", files.get(i).getFileName());
            }
        }
    }
}
