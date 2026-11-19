package com.yiguan.his.domain.enums;

/** 代煎单状态。 */
public enum DecoctionStatus {
    /** 待煎 */
    PENDING,
    /** 煎制中 */
    COOKING,
    /** 待取 */
    READY,
    /** 已交付 */
    DELIVERED,
    /** 已作废 */
    CANCELLED
}
