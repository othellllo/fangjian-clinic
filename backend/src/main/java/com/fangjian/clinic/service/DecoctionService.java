package com.fangjian.clinic.service;

import com.fangjian.clinic.common.BusinessException;
import com.fangjian.clinic.common.CodeGenerator;
import com.fangjian.clinic.common.PageResult;
import com.fangjian.clinic.domain.DecoctionOrder;
import com.fangjian.clinic.domain.Patient;
import com.fangjian.clinic.domain.Prescription;
import com.fangjian.clinic.domain.PrescriptionItem;
import com.fangjian.clinic.domain.SysUser;
import com.fangjian.clinic.domain.enums.DecoctionStatus;
import com.fangjian.clinic.dto.DecoctionDto;
import com.fangjian.clinic.repository.DecoctionOrderRepository;
import com.fangjian.clinic.repository.PatientRepository;
import com.fangjian.clinic.repository.PrescriptionItemRepository;
import com.fangjian.clinic.repository.PrescriptionRepository;
import com.fangjian.clinic.repository.SysUserRepository;
import com.fangjian.clinic.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 代煎流程：待煎 → 煎制中 → 待取 → 已交付。 */
@Service
public class DecoctionService {

    private final DecoctionOrderRepository repository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository itemRepository;
    private final PatientRepository patientRepository;
    private final SysUserRepository userRepository;

    public DecoctionService(DecoctionOrderRepository repository,
                            PrescriptionRepository prescriptionRepository,
                            PrescriptionItemRepository itemRepository,
                            PatientRepository patientRepository,
                            SysUserRepository userRepository) {
        this.repository = repository;
        this.prescriptionRepository = prescriptionRepository;
        this.itemRepository = itemRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
    }

    /** 中药代煎处方发药后自动建单。 */
    @Transactional
    public void createForPrescription(Prescription prescription, List<PrescriptionItem> items) {
        if (repository.existsByPrescriptionId(prescription.getId())) {
            return;
        }
        Patient patient = patientRepository.findById(prescription.getPatientId()).orElse(null);
        SysUser doctor = prescription.getDoctorId() == null ? null
                : userRepository.findById(prescription.getDoctorId()).orElse(null);

        DecoctionOrder order = new DecoctionOrder();
        order.setDecoctionNo(CodeGenerator.timeCode("D"));
        order.setPrescriptionId(prescription.getId());
        order.setPrescriptionNo(prescription.getPrescriptionNo());
        order.setPatientId(prescription.getPatientId());
        order.setPatientName(patient == null ? null : patient.getName());
        order.setDoctorId(prescription.getDoctorId());
        order.setDoctorName(doctor == null ? null : doctor.getRealName());
        order.setDoses(prescription.getDoses() == null ? 1 : prescription.getDoses());
        order.setPhone(patient == null ? null : patient.getPhone());
        order.setStatus(DecoctionStatus.PENDING);
        // 预计 2 小时后可取，具体以煎药房实际为准
        order.setExpectedTime(LocalDateTime.now().plusHours(2));
        order.setOperatorId(SecurityUtils.currentUserId());
        repository.save(order);
    }

    public PageResult<DecoctionDto> page(DecoctionStatus status, String keyword, int page, int size) {
        Page<DecoctionOrder> result = repository.search(status, keyword == null ? "" : keyword.trim(),
                PageRequest.of(Math.max(page - 1, 0), size));
        return PageResult.of(result, this::toDto);
    }

    public DecoctionDto detail(Long id) {
        return toDto(find(id));
    }

    public DecoctionOrder find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("代煎单"));
    }

    @Transactional
    public DecoctionDto cook(Long id) {
        DecoctionOrder order = find(id);
        requireStatus(order, DecoctionStatus.PENDING, "开始煎制");
        order.setStatus(DecoctionStatus.COOKING);
        order.setCookStartTime(LocalDateTime.now());
        repository.save(order);
        return toDto(order);
    }

    @Transactional
    public DecoctionDto ready(Long id) {
        DecoctionOrder order = find(id);
        requireStatus(order, DecoctionStatus.COOKING, "标记煎制完成");
        order.setStatus(DecoctionStatus.READY);
        order.setCookEndTime(LocalDateTime.now());
        repository.save(order);
        return toDto(order);
    }

    @Transactional
    public DecoctionDto deliver(Long id, String deliverType, String remark) {
        DecoctionOrder order = find(id);
        requireStatus(order, DecoctionStatus.READY, "交付");
        order.setStatus(DecoctionStatus.DELIVERED);
        order.setDeliverTime(LocalDateTime.now());
        order.setDeliverType(deliverType == null || deliverType.isBlank() ? "SELF" : deliverType);
        if (remark != null) {
            order.setRemark(remark);
        }
        repository.save(order);
        return toDto(order);
    }

    @Transactional
    public DecoctionDto cancel(Long id, String reason) {
        DecoctionOrder order = find(id);
        if (order.getStatus() == DecoctionStatus.DELIVERED) {
            throw new BusinessException("已交付的代煎单不能作废");
        }
        if (order.getStatus() == DecoctionStatus.CANCELLED) {
            throw new BusinessException("代煎单已作废");
        }
        order.setStatus(DecoctionStatus.CANCELLED);
        order.setRemark(reason == null ? "人工作废" : reason);
        repository.save(order);
        return toDto(order);
    }

    /** 各状态数量，用于工作台角标。 */
    public java.util.Map<String, Long> counts() {
        java.util.LinkedHashMap<String, Long> map = new java.util.LinkedHashMap<>();
        for (DecoctionStatus status : DecoctionStatus.values()) {
            map.put(status.name(), repository.countByStatus(status));
        }
        return map;
    }

    private void requireStatus(DecoctionOrder order, DecoctionStatus expected, String action) {
        if (order.getStatus() != expected) {
            throw new BusinessException("当前状态不允许" + action);
        }
    }

    private DecoctionDto toDto(DecoctionOrder order) {
        Prescription prescription = prescriptionRepository.findById(order.getPrescriptionId()).orElse(null);
        List<DecoctionDto.Item> items = itemRepository.findByPrescriptionId(order.getPrescriptionId()).stream()
                .map(item -> new DecoctionDto.Item(item.getDrugName(), item.getDose(), item.getQuantity(), item.getUnit()))
                .toList();
        return new DecoctionDto(order.getId(), order.getDecoctionNo(), order.getPrescriptionId(),
                order.getPrescriptionNo(), order.getPatientId(), order.getPatientName(), order.getDoctorName(),
                order.getDoses(), order.getStatus(),
                prescription == null ? null : prescription.getDecoctionMethod(),
                order.getExpectedTime(), order.getCookStartTime(), order.getCookEndTime(),
                order.getDeliverTime(), order.getDeliverType(), order.getPhone(), order.getRemark(),
                order.getCreatedAt(), items);
    }
}
