package com.fangjian.clinic.domain;

import com.fangjian.clinic.domain.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 员工账号。 */
@Entity
@Table(name = "sys_user")
@Getter
@Setter
@NoArgsConstructor
public class SysUser extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "real_name", nullable = false, length = 50)
    private String realName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(length = 50)
    private String title;

    @Column(length = 50)
    private String dept;

    @Column(length = 20)
    private String phone;

    /** 1 启用 / 0 停用。 */
    @Column(nullable = false)
    private Integer status = 1;
}
