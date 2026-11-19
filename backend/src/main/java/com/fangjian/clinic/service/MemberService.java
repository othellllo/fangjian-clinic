package com.fangjian.clinic.service;

import com.fangjian.clinic.common.BusinessException;
import com.fangjian.clinic.common.CodeGenerator;
import com.fangjian.clinic.common.PageResult;
import com.fangjian.clinic.domain.Member;
import com.fangjian.clinic.domain.MemberTxn;
import com.fangjian.clinic.domain.Patient;
import com.fangjian.clinic.domain.enums.MemberTxnType;
import com.fangjian.clinic.dto.MemberDto;
import com.fangjian.clinic.repository.MemberRepository;
import com.fangjian.clinic.repository.MemberTxnRepository;
import com.fangjian.clinic.repository.PatientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** 会员卡与储值。 */
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberTxnRepository txnRepository;
    private final PatientRepository patientRepository;

    public MemberService(MemberRepository memberRepository,
                         MemberTxnRepository txnRepository,
                         PatientRepository patientRepository) {
        this.memberRepository = memberRepository;
        this.txnRepository = txnRepository;
        this.patientRepository = patientRepository;
    }

    public static MemberDto toDto(Member member) {
        return new MemberDto(member.getId(), member.getMemberNo(), member.getPatientId(), member.getName(),
                member.getPhone(), member.getLevel(), member.getBalance(), member.getPoints(),
                member.getStatus(), member.getCreatedAt());
    }

    public PageResult<MemberDto> page(String keyword, int page, int size) {
        Page<Member> result = memberRepository.search(keyword == null ? "" : keyword.trim(),
                PageRequest.of(Math.max(page - 1, 0), size));
        return PageResult.of(result, MemberService::toDto);
    }

    public Member find(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("会员卡"));
    }

    @Transactional
    public MemberDto create(Long patientId, String name, String phone, Integer level) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> BusinessException.notFound("患者"));
        if (memberRepository.existsByPatientId(patientId)) {
            throw new BusinessException("该患者已开通过会员卡");
        }
        Member member = new Member();
        member.setMemberNo(CodeGenerator.dateCode("M"));
        member.setPatientId(patientId);
        member.setName(StringUtils.hasText(name) ? name : patient.getName());
        member.setPhone(StringUtils.hasText(phone) ? phone : patient.getPhone());
        member.setLevel(level == null ? 1 : level);
        member.setBalance(BigDecimal.ZERO);
        member.setPoints(0);
        member.setStatus(1);
        member = memberRepository.save(member);

        patient.setMemberId(member.getId());
        patientRepository.save(patient);
        return toDto(member);
    }

    public Map<String, Object> detail(Long id) {
        Member member = find(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("member", toDto(member));
        data.put("txns", txnRepository.findByMemberIdOrderByIdDesc(id, PageRequest.of(0, 50))
                .getContent().stream().map(MemberService::toTxnDto).toList());
        return data;
    }

    public PageResult<MemberDto.Txn> txns(Long id, int page, int size) {
        Page<MemberTxn> result = txnRepository.findByMemberIdOrderByIdDesc(id,
                PageRequest.of(Math.max(page - 1, 0), size));
        return PageResult.of(result, MemberService::toTxnDto);
    }

    @Transactional
    public MemberDto recharge(Long id, BigDecimal amount, String method, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("充值金额必须大于 0");
        }
        Member member = find(id);
        member.setBalance(member.getBalance().add(amount));
        memberRepository.save(member);
        writeTxn(member, MemberTxnType.RECHARGE, amount,
                "RECHARGE".equals(method) ? "充值" : "储值充值 " + (method == null ? "" : method), null, null);
        return toDto(member);
    }

    /** 收费时扣会员余额，返回扣款后余额。 */
    @Transactional
    public BigDecimal deductByPatient(Long patientId, BigDecimal amount, String refType, Long refId) {
        Member member = memberRepository.findByPatientId(patientId)
                .orElseThrow(() -> new BusinessException("该患者未开通会员卡，无法使用余额支付"));
        if (member.getBalance().compareTo(amount) < 0) {
            throw new BusinessException("会员余额不足，当前余额 " + member.getBalance().toPlainString());
        }
        member.setBalance(member.getBalance().subtract(amount));
        memberRepository.save(member);
        writeTxn(member, MemberTxnType.CONSUME, amount.negate(), "消费扣款", refType, refId);
        return member.getBalance();
    }

    /** 退费时把余额退回会员卡。 */
    @Transactional
    public void refundByPatient(Long patientId, BigDecimal amount, String refType, Long refId) {
        memberRepository.findByPatientId(patientId).ifPresent(member -> {
            member.setBalance(member.getBalance().add(amount));
            memberRepository.save(member);
            writeTxn(member, MemberTxnType.REFUND, amount, "退费退回余额", refType, refId);
        });
    }

    private void writeTxn(Member member, MemberTxnType type, BigDecimal amount,
                          String remark, String refType, Long refId) {
        MemberTxn txn = new MemberTxn();
        txn.setMemberId(member.getId());
        txn.setTxnType(type);
        txn.setAmount(amount);
        txn.setBalanceAfter(member.getBalance());
        txn.setRefType(refType);
        txn.setRefId(refId);
        txn.setRemark(remark);
        txnRepository.save(txn);
    }

    static MemberDto.Txn toTxnDto(MemberTxn txn) {
        return new MemberDto.Txn(txn.getId(), txn.getTxnType(), txn.getAmount(), txn.getBalanceAfter(),
                txn.getRemark(), txn.getCreatedAt());
    }
}
