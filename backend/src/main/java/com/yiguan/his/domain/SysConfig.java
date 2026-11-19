package com.yiguan.his.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 系统配置（医馆名称、地址等键值对）。 */
@Entity
@Table(name = "sys_config")
@Getter
@Setter
@NoArgsConstructor
public class SysConfig extends BaseEntity {

    @Column(name = "config_key", nullable = false, unique = true, length = 64)
    private String configKey;

    @Column(name = "config_value", length = 500)
    private String configValue;

    @Column(length = 200)
    private String remark;
}
