package com.fangjian.clinic.service;

import com.fangjian.clinic.common.BusinessException;
import com.fangjian.clinic.domain.DispenseScan;
import com.fangjian.clinic.domain.Drug;
import com.fangjian.clinic.domain.DrugTrace;
import com.fangjian.clinic.domain.Patient;
import com.fangjian.clinic.domain.Prescription;
import com.fangjian.clinic.domain.PrescriptionItem;
import com.fangjian.clinic.domain.enums.PrescriptionStatus;
import com.fangjian.clinic.domain.enums.TraceStatus;
import com.fangjian.clinic.repository.DispenseScanRepository;
import com.fangjian.clinic.repository.DrugRepository;
import com.fangjian.clinic.repository.DrugTraceRepository;
import com.fangjian.clinic.repository.PatientRepository;
import com.fangjian.clinic.repository.PrescriptionItemRepository;
import com.fangjian.clinic.repository.PrescriptionRepository;
import com.fangjian.clinic.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫码发药 / 扫码出库。
 *
 * <p>药房实际操作是「扫一下 → 库存就减一」，不再需要先点发药再核销，避免漏扫和重复操作。
 * 规则：
 * <ul>
 *   <li>中成药/西药/耗材（需追溯）→ 直接扫 20 位追溯码，扫一次核销一盒并扣库存；</li>
 *   <li>中药饮片（不需追溯）→ 扫药品条码或编码，可带数量（默认发完剩余量）；</li>
 *   <li>处方所有明细都扫够后自动完成发药；代煎处方自动进入煎药流程。</li>
 * </ul>
 */
@Service
public class PharmacyScanService {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository itemRepository;
    private final DrugRepository drugRepository;
    private final DrugTraceRepository traceRepository;
    private final DispenseScanRepository scanRepository;
    private final PatientRepository patientRepository;
    private final StockService stockService;
    private final ChargeService chargeService;
    private final DecoctionService decoctionService;

    public PharmacyScanService(PrescriptionRepository prescriptionRepository,
                               PrescriptionItemRepository itemRepository,
                               DrugRepository drugRepository,
                               DrugTraceRepository traceRepository,
                               DispenseScanRepository scanRepository,
                               PatientRepository patientRepository,
                               StockService stockService,
                               ChargeService chargeService,
                               DecoctionService decoctionService) {
        this.prescriptionRepository = prescriptionRepository;
        this.itemRepository = itemRepository;
        this.drugRepository = drugRepository;
        this.traceRepository = traceRepository;
        this.scanRepository = scanRepository;
        this.patientRepository = patientRepository;
        this.stockService = stockService;
        this.chargeService = chargeService;
        this.decoctionService = decoctionService;
    }

    /** 扫码发药：扫一次扣一次库存，全部扫完自动完成发药。 */
    @Transactional
    public Map<String, Object> scan(Long prescriptionId, String code, BigDecimal quantity, String traceCode) {
        if (!StringUtils.hasText(code) && !StringUtils.hasText(traceCode)) {
            throw new BusinessException("请扫描药品条码或追溯码");
        }
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> BusinessException.notFound("处方"));
        if (prescription.getStatus() != PrescriptionStatus.PENDING) {
            throw new BusinessException("该处方当前状态不允许发药");
        }
        if (!chargeService.isPaid(prescription.getChargeOrderId())) {
            throw new BusinessException("处方尚未收费，不能发药");
        }

        List<PrescriptionItem> items = itemRepository.findByPrescriptionId(prescriptionId);
        Map<Long, BigDecimal> scanned = scannedQty(prescriptionId);

        String scanValue = StringUtils.hasText(traceCode) ? traceCode.trim() : code.trim();
        String normalized = scanValue.toUpperCase();
        boolean traceMode = StringUtils.hasText(traceCode)
                || (normalized.length() == 20 && traceRepository.findByCode(normalized).isPresent());

        String drugName;
        BigDecimal deducted;
        String traceUsed = null;

        if (traceMode) {
            DrugTrace trace = traceRepository.findByCode(normalized)
                    .orElseThrow(() -> new BusinessException("追溯码 " + normalized + " 未入库绑定，无法出库"));
            if (trace.getStatus() != TraceStatus.IN_STOCK) {
                throw new BusinessException("追溯码 " + normalized + " 状态为「" + trace.getStatus() + "」，不能重复出库");
            }
            PrescriptionItem item = findItem(items, trace.getDrugId())
                    .orElseThrow(() -> new BusinessException("该追溯码对应的药品不在本处方中"));
            BigDecimal remaining = remaining(item, scanned);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("「" + item.getDrugName() + "」已发完，请勿重复扫码");
            }
            trace.setStatus(TraceStatus.DISPENSED);
            trace.setPrescriptionId(prescriptionId);
            trace.setPatientId(prescription.getPatientId());
            patientRepository.findById(prescription.getPatientId())
                    .map(Patient::getName).ifPresent(trace::setPatientName);
            trace.setDispenseTime(LocalDateTime.now());
            trace.setOperatorId(SecurityUtils.currentUserId());
            trace.setOperatorName(SecurityUtils.currentUser().realName());
            traceRepository.save(trace);

            drugName = item.getDrugName();
            deducted = BigDecimal.ONE;
            traceUsed = normalized;
        } else {
            Drug drug = resolveDrug(normalized)
                    .orElseThrow(() -> new BusinessException("未找到条码/编码对应的药品：" + scanValue));
            if (drug.getNeedTrace() != null && drug.getNeedTrace() == 1) {
                throw new BusinessException("「" + drug.getName() + "」是需追溯品种，请直接扫描包装上的 20 位追溯码");
            }
            PrescriptionItem item = findItem(items, drug.getId())
                    .orElseThrow(() -> new BusinessException("该药品不在本处方中"));
            BigDecimal remaining = remaining(item, scanned);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("「" + item.getDrugName() + "」已发完，请勿重复扫码");
            }
            BigDecimal qty = quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0 ? remaining : quantity;
            if (qty.compareTo(remaining) > 0) {
                throw new BusinessException("扫码数量超过剩余数量（剩余 "
                        + remaining.stripTrailingZeros().toPlainString() + "）");
            }
            drugName = item.getDrugName();
            deducted = qty;
        }

        // 扣库存（FEFO 跨批次），写流水
        Long drugId = traceMode
                ? traceRepository.findByCode(normalized).map(DrugTrace::getDrugId).orElse(null)
                : resolveDrug(normalized).map(Drug::getId).orElse(null);
        stockService.deduct(drugId, deducted, "PRESCRIPTION", prescriptionId);

        DispenseScan scan = new DispenseScan();
        scan.setPrescriptionId(prescriptionId);
        scan.setDrugId(drugId);
        scan.setDrugName(drugName);
        scan.setBarcode(traceMode ? null : normalized);
        scan.setTraceCode(traceUsed);
        scan.setQuantity(deducted);
        scan.setOperatorId(SecurityUtils.currentUserId());
        scan.setOperatorName(SecurityUtils.currentUser().realName());
        scanRepository.save(scan);

        // 进度与自动完成
        Map<Long, BigDecimal> after = scannedQty(prescriptionId);
        boolean completed = allFulfilled(items, after);
        if (completed) {
            prescription.setStatus(PrescriptionStatus.DISPENSED);
            prescriptionRepository.save(prescription);
            if (isDecoction(prescription)) {
                decoctionService.createForPrescription(prescription, items);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("drugName", drugName);
        data.put("quantity", deducted.stripTrailingZeros());
        data.put("completed", completed);
        data.put("progress", progress(prescriptionId, items));
        data.put("message", completed
                ? "全部药品已扫完，处方已发药完成"
                : "已出库：" + drugName + " " + deducted.stripTrailingZeros().toPlainString());
        return data;
    }

    /** 无处方直接出库（内部领用、报损等），需填原因。 */
    @Transactional
    public Map<String, Object> scanOut(String code, BigDecimal quantity, String reason) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("请扫描药品条码或追溯码");
        }
        String normalized = code.trim().toUpperCase();
        Long drugId;
        String drugName;
        BigDecimal qty = quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ONE : quantity;
        String traceUsed = null;

        DrugTrace trace = normalized.length() == 20 ? traceRepository.findByCode(normalized).orElse(null) : null;
        if (trace != null) {
            if (trace.getStatus() != TraceStatus.IN_STOCK) {
                throw new BusinessException("追溯码状态为「" + trace.getStatus() + "」，不能出库");
            }
            drugId = trace.getDrugId();
            drugName = trace.getDrugName();
            qty = BigDecimal.ONE;
            traceUsed = normalized;
            trace.setStatus(TraceStatus.DISPENSED);
            trace.setDispenseTime(LocalDateTime.now());
            trace.setOperatorId(SecurityUtils.currentUserId());
            trace.setOperatorName(SecurityUtils.currentUser().realName());
            traceRepository.save(trace);
        } else {
            Drug drug = resolveDrug(normalized)
                    .orElseThrow(() -> new BusinessException("未找到条码/编码对应的药品"));
            if (drug.getNeedTrace() != null && drug.getNeedTrace() == 1) {
                throw new BusinessException("「" + drug.getName() + "」是需追溯品种，请扫描 20 位追溯码出库");
            }
            drugId = drug.getId();
            drugName = drug.getName();
        }
        stockService.deduct(drugId, qty, "SCAN_OUT", null,
                StringUtils.hasText(reason) ? "直接出库：" + reason : "直接出库");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("drugName", drugName);
        data.put("quantity", qty.stripTrailingZeros());
        data.put("traceCode", traceUsed);
        data.put("reason", reason);
        data.put("stockAfter", stockService.stockOf(drugId));
        data.put("message", "已出库：" + drugName + " " + qty.stripTrailingZeros().toPlainString());
        return data;
    }

    /** 发药进度（含每味药 需要/已扫）。 */
    public List<Map<String, Object>> progress(Long prescriptionId, List<PrescriptionItem> items) {
        Map<Long, BigDecimal> scanned = scannedQty(prescriptionId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PrescriptionItem item : items) {
            BigDecimal done = scanned.getOrDefault(item.getDrugId(), BigDecimal.ZERO);
            BigDecimal required = item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity();
            Drug drug = drugRepository.findById(item.getDrugId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("drugId", item.getDrugId());
            row.put("drugName", item.getDrugName());
            row.put("unit", item.getUnit());
            row.put("required", required.stripTrailingZeros());
            row.put("scanned", done.stripTrailingZeros());
            row.put("needTrace", drug != null && drug.getNeedTrace() != null && drug.getNeedTrace() == 1);
            row.put("done", done.compareTo(required) >= 0);
            rows.add(row);
        }
        return rows;
    }

    public List<Map<String, Object>> progress(Long prescriptionId) {
        return progress(prescriptionId, itemRepository.findByPrescriptionId(prescriptionId));
    }

    /* ---------------- 内部 ---------------- */

    private Map<Long, BigDecimal> scannedQty(Long prescriptionId) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (DispenseScan scan : scanRepository.findByPrescriptionIdOrderByIdAsc(prescriptionId)) {
            map.merge(scan.getDrugId(), scan.getQuantity(), BigDecimal::add);
        }
        return map;
    }

    private java.util.Optional<PrescriptionItem> findItem(List<PrescriptionItem> items, Long drugId) {
        return items.stream().filter(item -> item.getDrugId().equals(drugId)).findFirst();
    }

    private BigDecimal remaining(PrescriptionItem item, Map<Long, BigDecimal> scanned) {
        BigDecimal done = scanned.getOrDefault(item.getDrugId(), BigDecimal.ZERO);
        BigDecimal required = item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity();
        return required.subtract(done);
    }

    private boolean allFulfilled(List<PrescriptionItem> items, Map<Long, BigDecimal> scanned) {
        for (PrescriptionItem item : items) {
            BigDecimal done = scanned.getOrDefault(item.getDrugId(), BigDecimal.ZERO);
            BigDecimal required = item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity();
            if (done.compareTo(required) < 0) {
                return false;
            }
        }
        return !items.isEmpty();
    }

    private java.util.Optional<Drug> resolveDrug(String value) {
        return drugRepository.findFirstByBarcode(value)
                .or(() -> drugRepository.findFirstByCode(value));
    }

    private boolean isDecoction(Prescription prescription) {
        return prescription.getType() == com.fangjian.clinic.domain.enums.PrescriptionType.HERB
                && prescription.getDecoctionMethod() != null
                && prescription.getDecoctionMethod().contains("代煎");
    }
}
