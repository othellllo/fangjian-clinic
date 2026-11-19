package com.fangjian.clinic.domain.enums;

/** 预约状态。 */
public enum AppointmentStatus {
    /** 已预约 */
    BOOKED,
    /** 已到店（已生成就诊记录） */
    ARRIVED,
    /** 已取消 */
    CANCELLED
}
