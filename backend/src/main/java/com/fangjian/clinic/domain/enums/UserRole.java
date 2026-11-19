package com.fangjian.clinic.domain.enums;

/** 员工角色。PATIENT 用于微信小程序患者端，与员工端 token 不通用。 */
public enum UserRole {
    ADMIN, DOCTOR, PHARMACIST, CASHIER, PATIENT
}
