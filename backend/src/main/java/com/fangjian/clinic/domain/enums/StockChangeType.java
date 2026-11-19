package com.fangjian.clinic.domain.enums;

/** 库存流水类型。 */
public enum StockChangeType {
    /** 入库 */
    IN,
    /** 出库（发药） */
    OUT,
    /** 盘点调整 */
    ADJUST,
    /** 退货 */
    RETURN
}
