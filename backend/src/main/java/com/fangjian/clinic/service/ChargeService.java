package com.fangjian.clinic.service;

import com.fangjian.clinic.common.BusinessException;
import com.fangjian.clinic.common.CodeGenerator;
import com.fangjian.clinic.common.PageResult;
import com.fangjian.clinic.domain.ChargeItem;
import com.fangjian.clinic.domain.ChargeOrder;
import com.fangjian.clinic.domain.Patient;
import com.fangjian.clinic.domain.Payment;
import com.fangjian.clinic.domain.Prescription;
import com.fangjian.clinic.domain.PrescriptionItem;
import com.fangjian.clinic.domain.Visit;
import com.fangjian.clinic.domain.enums.ChargeStatus;
import com.fangjian.clinic.domain.enums.ChargeType;
import com.fangjian.clinic.domain.enums.PayMethod;
import com.fangjian.clinic.dto.ChargeOrderDto;
import com.fangjian.clinic.repository.ChargeItemRepository;
import com.fangjian.clinic.repository.ChargeOrderRepository;
import com.fangjian.clinic.repository.PatientRepository;
import com.fangjian.clinic.repository.PaymentRepository;
import com.fangjian.clinic.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 收费、支付与退费。 */
@Service
public class ChargeService {

    private final ChargeOrderRepository orderRepository;
    private final ChargeItemRepository itemRepository;
    private final PaymentRepository paymentRepository;
    private final PatientRepository patientRepository;
    private final MemberService memberService;

    public ChargeService(ChargeOrderRepository orderRepository,
                         ChargeItemRepository itemRepository,
                         PaymentRepository paymentRepository,
                         PatientRepository patientRepository,
                         MemberService memberService) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.paymentRepository = paymentRepository;
        this.patientRepository = patientRepository;
        this.memberService = memberService;
    }

    /** 挂号时生成挂号费收费单。 */
    @Transactional
    public ChargeOrder createRegisterOrder(Visit visit, Patient patient, BigDecimal fee) {
        ChargeOrder order = newOrder(patient.getId(), visit.getId(), ChargeType.REGISTER, fee);
        order = orderRepository.save(order);

        ChargeItem item = new ChargeItem();
        item.setOrderId(order.getId());
        item.setItemType(ChargeType.REGISTER);
        item.setItemName("挂号费");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(fee);
        item.setAmount(fee);
        item.setRefId(visit.getId());
        itemRepository.save(item);
        return order;
    }

    /** 开方时生成处方收费单。 */
    @Transactional
    public ChargeOrder createPrescriptionOrder(Prescription prescription, List<PrescriptionItem> items, Patient patient) {
        BigDecimal total = items.stream()
                .map(item -> item.getAmount() == null ? BigDecimal.ZERO : item.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ChargeOrder order = newOrder(patient.getId(), prescription.getVisitId(), ChargeType.PRESCRIPTION, total);
        order = orderRepository.save(order);

        List<ChargeItem> chargeItems = new ArrayList<>(items.size());
        for (PrescriptionItem source : items) {
            ChargeItem item = new ChargeItem();
            item.setOrderId(order.getId());
            item.setItemType(ChargeType.PRESCRIPTION);
            item.setItemName(source.getDrugName());
            item.setDrugId(source.getDrugId());
            item.setQuantity(source.getQuantity());
            item.setUnitPrice(source.getPrice());
            item.setAmount(source.getAmount());
            item.setRefId(prescription.getId());
            chargeItems.add(item);
        }
        itemRepository.saveAll(chargeItems);
        return order;
    }

    private ChargeOrder newOrder(Long patientId, Long visitId, ChargeType type, BigDecimal amount) {
        BigDecimal total = amount == null ? BigDecimal.ZERO : amount;
        ChargeOrder order = new ChargeOrder();
        order.setOrderNo(CodeGenerator.timeCode("C"));
        order.setPatientId(patientId);
        order.setVisitId(visitId);
        order.setType(type);
        order.setTotalAmount(total);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayableAmount(total);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setStatus(ChargeStatus.UNPAID);
        return order;
    }

    public ChargeOrder find(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("收费单"));
    }

    public boolean isPaid(Long orderId) {
        if (orderId == null) {
            return false;
        }
        return orderRepository.findById(orderId)
                .map(order -> order.getStatus() == ChargeStatus.PAID)
                .orElse(false);
    }

    public ChargeOrderDto detail(Long id) {
        return toDto(find(id), true);
    }

    public PageResult<ChargeOrderDto> page(ChargeStatus status, ChargeType type, LocalDate date,
                                           int page, int size) {
        LocalDateTime from = date == null ? null : date.atStartOfDay();
        LocalDateTime to = date == null ? null : date.plusDays(1).atStartOfDay();
        Page<ChargeOrder> result = orderRepository.search(status, type, from, to,
                PageRequest.of(Math.max(page - 1, 0), size));
        return PageResult.of(result, order -> toDto(order, false));
    }

    @Transactional
    public ChargeOrderDto pay(Long id, PayMethod method, String remark) {
        ChargeOrder order = find(id);
        if (order.getStatus() != ChargeStatus.UNPAID) {
            throw new BusinessException("该收费单已收费或已退费，请勿重复操作");
        }
        if (method == null) {
            throw new BusinessException("请选择支付方式");
        }
        BigDecimal amount = order.getPayableAmount();
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("收费金额异常");
        }
        if (method == PayMethod.MEMBER_BALANCE) {
            memberService.deductByPatient(order.getPatientId(), amount, "CHARGE_ORDER", order.getId());
        }
        order.setStatus(ChargeStatus.PAID);
        order.setPayMethod(method);
        order.setPaidAmount(amount);
        order.setPaidAt(LocalDateTime.now());
        order.setRemark(remark);
        orderRepository.save(order);

        writePayment(order, method, amount, remark);
        return toDto(order, true);
    }

    @Transactional
    public ChargeOrderDto refund(Long id, String reason) {
        ChargeOrder order = find(id);
        if (order.getStatus() != ChargeStatus.PAID) {
            throw new BusinessException("只有已收费的收费单才能退费");
        }
        if (order.getPayMethod() == PayMethod.MEMBER_BALANCE) {
            memberService.refundByPatient(order.getPatientId(), order.getPaidAmount(), "CHARGE_ORDER", order.getId());
        }
        order.setStatus(ChargeStatus.REFUNDED);
        order.setRemark(reason);
        orderRepository.save(order);

        Payment refundPayment = new Payment();
        refundPayment.setOrderId(order.getId());
        refundPayment.setMethod(order.getPayMethod());
        refundPayment.setAmount(order.getPaidAmount().negate());
        refundPayment.setOperatorId(SecurityUtils.currentUserId());
        refundPayment.setOperatorName(SecurityUtils.currentUser().realName());
        refundPayment.setRemark("退费：" + (reason == null ? "" : reason));
        paymentRepository.save(refundPayment);
        return toDto(order, true);
    }

    @Transactional
    public void cancelUnpaidOrder(Long orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.getStatus() == ChargeStatus.UNPAID) {
                order.setStatus(ChargeStatus.REFUNDED);
                order.setRemark("业务作废，自动关闭收费单");
                orderRepository.save(order);
            }
        });
    }

    private void writePayment(ChargeOrder order, PayMethod method, BigDecimal amount, String remark) {
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setMethod(method);
        payment.setAmount(amount);
        payment.setOperatorId(SecurityUtils.currentUserId());
        payment.setOperatorName(SecurityUtils.currentUser().realName());
        payment.setRemark(remark);
        paymentRepository.save(payment);
    }

    /** 收费员日结：总额、笔数、按支付方式分布。 */
    public Map<String, Object> summary(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();
        List<Payment> payments = paymentRepository.findByCreatedAtBetweenOrderByIdAsc(from, to);

        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> byMethod = new TreeMap<>();
        for (Payment payment : payments) {
            total = total.add(payment.getAmount());
            String key = payment.getMethod() == null ? "UNKNOWN" : payment.getMethod().name();
            byMethod.merge(key, payment.getAmount(), BigDecimal::add);
        }
        List<Map<String, Object>> methodList = new ArrayList<>();
        byMethod.forEach((method, amount) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("method", method);
            row.put("amount", amount);
            methodList.add(row);
        });

        long count = payments.stream().filter(p -> p.getAmount().compareTo(BigDecimal.ZERO) > 0).count();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", day.toString());
        data.put("total", total);
        data.put("count", count);
        data.put("byMethod", methodList);
        return data;
    }

    ChargeOrderDto toDto(ChargeOrder order, boolean withItems) {
        String patientName = patientRepository.findById(order.getPatientId())
                .map(Patient::getName).orElse(null);
        List<ChargeOrderDto.Item> items = withItems
                ? itemRepository.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .map(item -> new ChargeOrderDto.Item(item.getId(), item.getItemType(), item.getItemName(),
                        item.getQuantity(), item.getUnitPrice(), item.getAmount()))
                .toList()
                : List.of();
        return new ChargeOrderDto(order.getId(), order.getOrderNo(), order.getVisitId(), order.getPatientId(),
                patientName, order.getType(), order.getTotalAmount(), order.getDiscountAmount(),
                order.getPayableAmount(), order.getPaidAmount(), order.getStatus(), order.getPayMethod(),
                order.getCreatedAt(), order.getPaidAt(), items);
    }
}
