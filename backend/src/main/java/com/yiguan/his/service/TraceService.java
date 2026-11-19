package com.yiguan.his.service;

import com.yiguan.his.common.BusinessException;
import com.yiguan.his.common.PageResult;
import com.yiguan.his.domain.Drug;
import com.yiguan.his.domain.DrugTrace;
import com.yiguan.his.domain.Patient;
import com.yiguan.his.domain.Prescription;
import com.yiguan.his.domain.PrescriptionItem;
import com.yiguan.his.domain.enums.TraceStatus;
import com.yiguan.his.dto.TraceDto;
import com.yiguan.his.integration.TraceUploadProvider;
import com.yiguan.his.repository.DrugRepository;
import com.yiguan.his.repository.DrugTraceRepository;
import com.yiguan.his.repository.PatientRepository;
import com.yiguan.his.repository.PrescriptionItemRepository;
import com.yiguan.his.repository.PrescriptionRepository;
import com.yiguan.his.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 药品追溯码：入库绑定、台账、发药核销、退药回退、上传。
 *
 * <p>合规依据见 docs/policy-brief.md：中成药/西药/耗材需采集，中药饮片暂不强制。</p>
 */
@Service
public class TraceService {

    private static final int CODE_LENGTH = 20;

    private final DrugTraceRepository traceRepository;
    private final DrugRepository drugRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository itemRepository;
    private final PatientRepository patientRepository;
    private final List<TraceUploadProvider> uploadProviders;

    public TraceService(DrugTraceRepository traceRepository,
                        DrugRepository drugRepository,
                        PrescriptionRepository prescriptionRepository,
                        PrescriptionItemRepository itemRepository,
                        PatientRepository patientRepository,
                        List<TraceUploadProvider> uploadProviders) {
        this.traceRepository = traceRepository;
        this.drugRepository = drugRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.itemRepository = itemRepository;
        this.patientRepository = patientRepository;
        this.uploadProviders = uploadProviders;
    }

    /** 入库扫码绑定。返回成功数、失败数与失败原因。 */
    @Transactional
    public TraceDto.BindResult bind(Long drugId, String batchNo, List<String> codes) {
        Drug drug = drugRepository.findById(drugId)
                .orElseThrow(() -> BusinessException.notFound("药品"));
        if (codes == null || codes.isEmpty()) {
            throw new BusinessException("请扫描至少一个追溯码");
        }
        if (!StringUtils.hasText(batchNo)) {
            throw new BusinessException("请选择或填写批号");
        }
        List<String> errors = new ArrayList<>();
        int success = 0;
        for (String raw : codes) {
            String code = raw == null ? "" : raw.trim().toUpperCase();
            if (!isValidCode(code)) {
                errors.add(code + "：格式不正确（应为 20 位字母或数字）");
                continue;
            }
            if (traceRepository.existsByCode(code)) {
                errors.add(code + "：已存在（重复扫码或已被其他批次绑定）");
                continue;
            }
            DrugTrace trace = new DrugTrace();
            trace.setDrugId(drug.getId());
            trace.setDrugName(drug.getName());
            trace.setBatchNo(batchNo.trim());
            trace.setCode(code);
            trace.setStatus(TraceStatus.IN_STOCK);
            trace.setBindTime(LocalDateTime.now());
            trace.setOperatorId(SecurityUtils.currentUserId());
            trace.setOperatorName(SecurityUtils.currentUser().realName());
            trace.setUploadStatus("PENDING");
            traceRepository.save(trace);
            success++;
        }
        return new TraceDto.BindResult(success, errors.size(), errors.size() > 20 ? errors.subList(0, 20) : errors);
    }

    public PageResult<TraceDto> page(Long drugId, String batchNo, TraceStatus status, String keyword,
                                     int page, int size) {
        Page<DrugTrace> result = traceRepository.search(drugId,
                StringUtils.hasText(batchNo) ? batchNo.trim() : null,
                status,
                keyword == null ? "" : keyword.trim(),
                PageRequest.of(Math.max(page - 1, 0), size));
        return PageResult.of(result, TraceService::toDto);
    }

    public TraceDto byCode(String code) {
        return traceRepository.findByCode(normalize(code))
                .map(TraceService::toDto)
                .orElseThrow(() -> BusinessException.notFound("追溯码"));
    }

    public List<TraceDto> listByPrescription(Long prescriptionId) {
        return traceRepository.findByPrescriptionIdOrderByIdAsc(prescriptionId).stream()
                .map(TraceService::toDto).toList();
    }

    public Map<String, Long> counts() {
        Map<String, Long> map = new java.util.LinkedHashMap<>();
        map.put("IN_STOCK", traceRepository.countByStatus(TraceStatus.IN_STOCK));
        map.put("DISPENSED", traceRepository.countByStatus(TraceStatus.DISPENSED));
        map.put("RETURNED", traceRepository.countByStatus(TraceStatus.RETURNED));
        return map;
    }

    /**
     * 发药核销：按药品校验扫码数量并绑定到处方。
     *
     * @param provided 每个药品提供的追溯码；需追溯药品必须足量
     */
    @Transactional
    public void dispenseForPrescription(Prescription prescription, List<PrescriptionItem> items,
                                        Map<Long, List<String>> provided) {
        Long patientId = prescription.getPatientId();
        Patient patient = patientRepository.findById(patientId).orElse(null);
        Map<Long, List<String>> used = new HashMap<>();

        for (PrescriptionItem item : items) {
            Drug drug = drugRepository.findById(item.getDrugId()).orElse(null);
            boolean needTrace = drug != null && drug.getNeedTrace() != null && drug.getNeedTrace() == 1;
            if (!needTrace) {
                continue;
            }
            int required = item.getQuantity() == null ? 0
                    : item.getQuantity().setScale(0, java.math.RoundingMode.CEILING).intValue();
            List<String> codes = provided.get(item.getDrugId());
            int got = codes == null ? 0 : codes.size();
            if (got < required) {
                throw new BusinessException("药品「" + item.getDrugName() + "」需扫描追溯码 "
                        + required + " 个，当前 " + got + " 个，请补扫后再发药");
            }
            for (String raw : codes) {
                String code = normalize(raw);
                DrugTrace trace = traceRepository.findByCode(code)
                        .orElseThrow(() -> new BusinessException("追溯码 " + code + " 未入库绑定，请先在「追溯码管理」入库"));
                if (trace.getStatus() != TraceStatus.IN_STOCK) {
                    throw new BusinessException("追溯码 " + code + " 状态为「" + trace.getStatus()
                            + "」，不能重复发药");
                }
                if (!trace.getDrugId().equals(item.getDrugId())) {
                    throw new BusinessException("追溯码 " + code + " 属于其他药品，与「" + item.getDrugName() + "」不匹配");
                }
                trace.setStatus(TraceStatus.DISPENSED);
                trace.setPrescriptionId(prescription.getId());
                trace.setPatientId(patientId);
                trace.setPatientName(patient == null ? null : patient.getName());
                trace.setDispenseTime(LocalDateTime.now());
                trace.setOperatorId(SecurityUtils.currentUserId());
                trace.setOperatorName(SecurityUtils.currentUser().realName());
                traceRepository.save(trace);
                used.computeIfAbsent(item.getDrugId(), key -> new ArrayList<>()).add(code);
            }
        }
    }

    /** 上传医保平台（未接入时返回 UNSUPPORTED，保留本地待补传）。 */
    @Transactional
    public Map<String, Object> upload(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要上传的追溯码");
        }
        List<DrugTrace> traces = traceRepository.findByIdIn(ids);
        TraceUploadProvider provider = uploadProviders.stream()
                .filter(TraceUploadProvider::enabled).findFirst()
                .orElse(uploadProviders.isEmpty() ? null : uploadProviders.get(0));
        String status = "UNSUPPORTED";
        String message = "未配置上传实现";
        if (provider != null) {
            TraceUploadProvider.UploadResult result = provider.upload(traces.stream().map(TraceService::toDto).toList());
            status = result.status();
            message = result.message();
        }
        if ("SUCCESS".equals(status)) {
            LocalDateTime now = LocalDateTime.now();
            for (DrugTrace trace : traces) {
                trace.setUploadStatus("UPLOADED");
                trace.setUploadTime(now);
            }
            traceRepository.saveAll(traces);
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("status", status);
        data.put("message", message);
        data.put("count", traces.size());
        return data;
    }

    /** 退药：追溯码回退为在库，保留退药时间与操作人。 */
    @Transactional
    public Map<String, Object> returnBack(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要退药的追溯码");
        }
        List<DrugTrace> traces = traceRepository.findByIdIn(ids);
        int changed = 0;
        for (DrugTrace trace : traces) {
            if (trace.getStatus() != TraceStatus.DISPENSED) {
                continue;
            }
            trace.setStatus(TraceStatus.IN_STOCK);
            trace.setReturnTime(LocalDateTime.now());
            trace.setPrescriptionId(null);
            trace.setPatientId(null);
            trace.setPatientName(null);
            trace.setDispenseTime(null);
            traceRepository.save(trace);
            changed++;
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("changed", changed);
        data.put("total", traces.size());
        return data;
    }

    /** 校验是否为 20 位追溯码。 */
    public boolean isValidCode(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            return false;
        }
        for (char ch : code.toCharArray()) {
            if (!Character.isLetterOrDigit(ch)) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    static TraceDto toDto(DrugTrace trace) {
        return new TraceDto(trace.getId(), trace.getDrugId(), trace.getDrugName(), trace.getBatchNo(),
                trace.getCode(), trace.getStatus(), trace.getBindTime(), trace.getPrescriptionId(),
                trace.getPatientName(), trace.getDispenseTime(), trace.getOperatorName(),
                trace.getUploadStatus());
    }
}
