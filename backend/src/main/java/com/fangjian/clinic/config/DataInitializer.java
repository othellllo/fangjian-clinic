package com.fangjian.clinic.config;

import com.fangjian.clinic.domain.SysUser;
import com.fangjian.clinic.domain.enums.UserRole;
import com.fangjian.clinic.repository.SysConfigRepository;
import com.fangjian.clinic.repository.SysUserRepository;
import com.fangjian.clinic.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动初始化：演示账号与医馆基础配置。
 * 账号只在用户表为空时创建，不会覆盖已有账号。
 */
@Component
@Order(0)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String DEFAULT_PASSWORD = "123456";

    private final SysUserRepository userRepository;
    private final SysConfigRepository configRepository;
    private final ConfigService configService;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserRepository userRepository,
                           SysConfigRepository configRepository,
                           ConfigService configService,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.configRepository = configRepository;
        this.configService = configService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            createUser("admin", "张馆长", UserRole.ADMIN, "馆长", "院办", "13800000000");
            createUser("doctor01", "李青囊", UserRole.DOCTOR, "主任中医师", "中医内科", "13800000001");
            createUser("doctor02", "陈远志", UserRole.DOCTOR, "副主任中医师", "针灸推拿科", "13800000002");
            createUser("doctor03", "苏佩兰", UserRole.DOCTOR, "主治中医师", "中医妇科", "13800000003");
            createUser("cashier01", "周当归", UserRole.CASHIER, null, "收费处", "13800000004");
            createUser("pharmacist01", "吴茱萸", UserRole.PHARMACIST, null, "中药房", "13800000005");
            log.info("已初始化演示账号，默认密码 {}", DEFAULT_PASSWORD);
        }
        seedConfig(ConfigService.KEY_CLINIC_NAME, "示范中医馆", "医馆名称");
        seedConfig(ConfigService.KEY_CLINIC_ADDRESS, "示例市青囊路 18 号", "医馆地址");
        seedConfig(ConfigService.KEY_CLINIC_PHONE, "0571-88880000", "联系电话");
        seedConfig(ConfigService.KEY_CLINIC_NOTICE, "本系统为开源演示版，数据请勿用于真实诊疗。", "公告");
        seedConfig(ConfigService.KEY_REGISTER_FEE, "20.00", "普通挂号费");
    }

    private void createUser(String username, String realName, UserRole role, String title, String dept, String phone) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setRealName(realName);
        user.setRole(role);
        user.setTitle(title);
        user.setDept(dept);
        user.setPhone(phone);
        user.setStatus(1);
        userRepository.save(user);
    }

    private void seedConfig(String key, String value, String remark) {
        if (configRepository.findByConfigKey(key).isEmpty()) {
            configService.set(key, value, remark);
        }
    }
}
