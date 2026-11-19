package com.yiguan.his.service;

import com.yiguan.his.common.BusinessException;
import com.yiguan.his.common.CodeGenerator;
import com.yiguan.his.domain.Drug;
import com.yiguan.his.domain.Patient;
import com.yiguan.his.domain.Prescription;
import com.yiguan.his.domain.PrescriptionItem;
import com.yiguan.his.domain.Visit;
import com.yiguan.his.domain.enums.ChargeStatus;
import com.yiguan.his.domain.enums.PrescriptionStatus;
import com.yiguan.his.domain.enums.PrescriptionType;
import com.yiguan.his.domain.enums.VisitStatus;
import com.yiguan.his.dto.PrescriptionDto;
import com.yiguan.his.repository.DrugRepository;
import com.yiguan.his.repository.PatientRepository;
import com.yiguan.his.repository.PrescriptionItemRepository;
import com.yiguan.his.repository.PrescriptionRepository;
import com.yiguan.his.repository.VisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 处方开立与发药。 */
@Service
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository itemRepository;
    private final DrugRepository drugRepository;
    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final ChargeService chargeService;
    private final StockService stockService;
    private final DecoctionService decoctionService;
    private final TraceService traceService;
    private final PrescriptionCheckService prescriptionCheckService;
    private final com.yiguan.his.repository.DispenseScanRepository dispenseScanRepository;
    private final ViewAssembler viewAssembler;

    public PrescriptionService(PrescriptionRepository prescriptionRepository,
                               PrescriptionItemRepository itemRepository,
                               DrugRepository drugRepository,
                               VisitRepository visitRepository,
                               PatientRepository patientRepository,
                               ChargeService chargeService,
                               StockService stockService,
                               DecoctionService decoctionService,
                               TraceService traceService,
                               PrescriptionCheckService prescriptionCheckService,
                               com.yiguan.his.repository.DispenseScanRepository dispenseScanRepository,
                               ViewAssembler viewAssembler) {
        this.prescriptionRepository = prescriptionRepository;
        this.itemRepository = itemRepository;
        this.drugRepository = drugRepository;
        this.visitRepository = visitRepository;
        this.patientRepository = patientRepository;
        this.chargeService = chargeService;
        this.stockService = stockService;
        this.decoctionService = decoctionService;
        this.traceService = traceService;
        this.prescriptionCheckService = prescriptionCheckService;
        this.dispenseScanRepository = dispenseScanRepository;
        this.viewAssembler = viewAssembler;
    }

    public List<PrescriptionDto> listByVisit(Long visitId) {
        List<Prescription> prescriptions = prescriptionRepository.findByVisitIdOrderByIdDesc(visitId);
        return prescriptions.stream().map(this::toDto).toList();
    }

    public PrescriptionDto detail(Long id) {
        return toDto(find(id));
    }

    public Prescription find(Long id) {
        return prescriptionRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("处方"));
    }

    @Transactional
    public PrescriptionDto create(CreateRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("处方不能没有药品明细");
        }
        Visit visit = visitRepository.findById(request.visitId())
                .orElseThrow(() -> BusinessException.notFound("就诊记录"));
        if (visit.getStatus() != VisitStatus.IN_SERVICE) {
            throw new BusinessException("请先接诊后再开方");
        }
        Patient patient = patientRepository.findById(visit.getPatientId())
                .orElseThrow(() -> BusinessException.notFound("患者"));

        int doses = request.doses() == null || request.doses() < 1 ? 1 : request.doses();
        Prescription prescription = new Prescription();
        prescription.setPrescriptionNo(CodeGenerator.timeCode("R"));
        prescription.setVisitId(visit.getId());
        prescription.setPatientId(patient.getId());
        prescription.setDoctorId(visit.getDoctorId());
        prescription.setType(request.type() == null ? PrescriptionType.HERB : request.type());
        prescription.setDecoctionMethod(request.decoctionMethod());
        prescription.setDoses(doses);
        prescription.setRemark(request.remark());
        prescription.setStatus(PrescriptionStatus.PENDING);
        prescription = prescriptionRepository.save(prescription);

        List<PrescriptionItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ItemRequest source : request.items()) {
            Drug drug = drugRepository.findById(source.drugId())
                    .orElseThrow(() -> BusinessException.notFound("药品"));
            if (drug.getStatus() != null && drug.getStatus() == 0) {
                throw new BusinessException("药品「" + drug.getName() + "」已停用");
            }
            BigDecimal quantity = source.quantity() == null ? BigDecimal.ZERO : source.quantity();
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("药品「" + drug.getName() + "」数量必须大于 0");
            }
            PrescriptionItem item = new PrescriptionItem();
            item.setPrescriptionId(prescription.getId());
            item.setDrugId(drug.getId());
            item.setDrugName(drug.getName());
            item.setSpec(drug.getSpec());
            item.setUnit(drug.getUnit());
            item.setDose(source.dose());
            item.setQuantity(quantity);
            item.setPrice(drug.getPrice());
            item.setAmount(drug.getPrice().multiply(quantity).setScale(2, java.math.RoundingMode.HALF_UP));
            // 特殊煎法自动标注（先煎/后下/包煎/另煎/烊化/冲服）
            item.setUsageText(PrescriptionCheckService.appendHandling(source.usageText(),
                    drug.getSpecialHandling()));
            total = total.add(item.getAmount());
            items.add(item);
        }

        // 用药安全审查：十八反/十九畏/妊娠禁忌阻断，毒性/剂量提示
        java.util.List<com.yiguan.his.dto.QcResult.DoseUsage> usages = items.stream()
                .map(item -> new com.yiguan.his.dto.QcResult.DoseUsage(item.getDrugId(), item.getDose(), item.getQuantity()))
                .toList();
        Boolean pregnant = patient.getPregnant() == null ? null : patient.getPregnant() == 1;
        var check = prescriptionCheckService.check(usages, pregnant);
        var blockers = check.issues().stream()
                .filter(issueItem -> issueItem.level() == com.yiguan.his.domain.enums.IssueLevel.BLOCK)
                .map(issueItem -> issueItem.message() + "（" + issueItem.advice() + "）")
                .toList();
        if (!blockers.isEmpty()) {
            throw new BusinessException("处方存在用药禁忌：" + String.join("；", blockers));
        }

        itemRepository.saveAll(items);

        prescription.setTotalAmount(total);
        var order = chargeService.createPrescriptionOrder(prescription, items, patient);
        prescription.setChargeOrderId(order.getId());
        prescriptionRepository.save(prescription);
        return toDto(prescription);
    }

    /** 已收费、待发药的处方。 */
    public List<PrescriptionDto> pendingDispense() {
        return prescriptionRepository.findDispensable(PrescriptionStatus.PENDING, ChargeStatus.PAID)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public PrescriptionDto dispense(Long id) {
        return dispense(id, java.util.List.of());
    }

    /**
     * 发药。含需追溯药品的处方必须携带对应追溯码（见 docs/policy-brief.md）。
     */
    @Transactional
    public PrescriptionDto dispense(Long id, java.util.List<TraceInput> traces) {
        Prescription prescription = find(id);
        if (prescription.getStatus() != PrescriptionStatus.PENDING) {
            throw new BusinessException("该处方当前状态不允许发药");
        }
        if (!chargeService.isPaid(prescription.getChargeOrderId())) {
            throw new BusinessException("处方尚未收费，不能发药");
        }
        // 已使用扫码发药的处方，禁止整单发药，避免重复扣库存
        if (!dispenseScanRepository.findByPrescriptionIdOrderByIdAsc(id).isEmpty()) {
            throw new BusinessException("该处方已使用「扫码发药」，请继续扫码出库至完成");
        }
        List<PrescriptionItem> items = itemRepository.findByPrescriptionId(id);

        // 先做追溯码校验与核销，失败时整单回滚，不会出现"扣了库存没扫码"的中间态
        java.util.Map<Long, java.util.List<String>> provided = new java.util.HashMap<>();
        if (traces != null) {
            for (TraceInput input : traces) {
                if (input.drugId() != null && input.codes() != null && !input.codes().isEmpty()) {
                    provided.put(input.drugId(), input.codes());
                }
            }
        }
        traceService.dispenseForPrescription(prescription, items, provided);

        for (PrescriptionItem item : items) {
            stockService.deduct(item.getDrugId(), item.getQuantity(), "PRESCRIPTION", prescription.getId());
        }
        prescription.setStatus(PrescriptionStatus.DISPENSED);
        prescriptionRepository.save(prescription);
        // 中药代煎：发药后自动进入煎药房流程
        if (isDecoction(prescription)) {
            decoctionService.createForPrescription(prescription, items);
        }
        return toDto(prescription);
    }

    /** 发药时的追溯码入参。 */
    public record TraceInput(Long drugId, java.util.List<String> codes) {
    }

    private boolean isDecoction(Prescription prescription) {
        return prescription.getType() == PrescriptionType.HERB
                && prescription.getDecoctionMethod() != null
                && prescription.getDecoctionMethod().contains("代煎");
    }

    @Transactional
    public PrescriptionDto cancel(Long id) {
        Prescription prescription = find(id);
        if (prescription.getStatus() == PrescriptionStatus.DISPENSED) {
            throw new BusinessException("已发药的处方不能作废");
        }
        if (chargeService.isPaid(prescription.getChargeOrderId())) {
            throw new BusinessException("处方已收费，请先退费再作废");
        }
        chargeService.cancelUnpaidOrder(prescription.getChargeOrderId());
        prescription.setStatus(PrescriptionStatus.CANCELLED);
        prescriptionRepository.save(prescription);
        return toDto(prescription);
    }

    private PrescriptionDto toDto(Prescription prescription) {
        List<PrescriptionDto.Item> items = itemRepository.findByPrescriptionId(prescription.getId()).stream()
                .map(item -> new PrescriptionDto.Item(item.getId(), item.getDrugId(), item.getDrugName(),
                        item.getSpec(), item.getUnit(), item.getDose(), item.getQuantity(),
                        item.getPrice(), item.getAmount(), item.getUsageText()))
                .toList();
        return new PrescriptionDto(prescription.getId(), prescription.getPrescriptionNo(),
                prescription.getVisitId(),
                viewAssembler.patientName(prescription.getPatientId()),
                viewAssembler.userName(prescription.getDoctorId()),
                prescription.getType(), prescription.getDecoctionMethod(), prescription.getDoses(),
                prescription.getTotalAmount(), prescription.getStatus(), prescription.getRemark(),
                prescription.getCreatedAt(), prescription.getChargeOrderId(), items);
    }

    /** 开方请求。 */
    public record CreateRequest(Long visitId, PrescriptionType type, String decoctionMethod,
                                Integer doses, String remark, List<ItemRequest> items) {
    }

    /** 处方明细请求。 */
    public record ItemRequest(Long drugId, BigDecimal dose, BigDecimal quantity, String usageText) {
    }
}
