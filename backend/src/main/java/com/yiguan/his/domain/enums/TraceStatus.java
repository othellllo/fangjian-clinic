package com.yiguan.his.domain.enums;

/** 追溯码状态。 */
public enum TraceStatus {
    /** 在库 */
    IN_STOCK,
    /** 已发药 */
    DISPENSED,
    /** 已退药 */
    RETURNED,
    /** 作废 */
    VOID
}
