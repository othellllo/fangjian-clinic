package com.fangjian.clinic.domain.enums;

/** 就诊状态。 */
public enum VisitStatus {
    /** 候诊 */
    WAITING,
    /** 就诊中 */
    IN_SERVICE,
    /** 已完成 */
    FINISHED,
    /** 已作废 */
    CANCELLED
}
