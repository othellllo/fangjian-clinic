package com.yiguan.his.dto;

import com.yiguan.his.domain.enums.MemberTxnType;
import com.yiguan.his.domain.enums.UserRole;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 会员视图。 */
public record MemberDto(
        Long id,
        String memberNo,
        Long patientId,
        String name,
        String phone,
        Integer level,
        BigDecimal balance,
        Integer points,
        Integer status,
        LocalDateTime createdAt) {

    /** 储值流水视图。 */
    public record Txn(
            Long id,
            MemberTxnType txnType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String remark,
            LocalDateTime createdAt) {
    }

    /** 员工视图。 */
    public record User(
            Long id,
            String username,
            String realName,
            UserRole role,
            String title,
            String dept,
            String phone,
            Integer status) {
    }
}
