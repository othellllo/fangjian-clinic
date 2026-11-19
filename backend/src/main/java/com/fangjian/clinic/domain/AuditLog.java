package com.fangjian.clinic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 操作审计日志：记录谁在什么时候对什么做了什么，用于事后追溯。 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 50)
    private String username;

    @Column(name = "real_name", length = 50)
    private String realName;

    @Column(length = 20)
    private String role;

    @Column(nullable = false, length = 50)
    private String module;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(length = 2000)
    private String detail;

    @Column(length = 64)
    private String ip;

    /** SUCCESS / FAIL */
    @Column(nullable = false, length = 20)
    private String result = "SUCCESS";
}
