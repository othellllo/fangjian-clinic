package com.fangjian.clinic.service;

import com.fangjian.clinic.common.BusinessException;
import com.fangjian.clinic.common.PageResult;
import com.fangjian.clinic.domain.Drug;
import com.fangjian.clinic.domain.DrugBatch;
import com.fangjian.clinic.domain.StockLedger;
import com.fangjian.clinic.domain.enums.StockChangeType;
import com.fangjian.clinic.dto.DrugDto;
import com.fangjian.clinic.repository.DrugBatchRepository;
import com.fangjian.clinic.repository.DrugRepository;
import com.fangjian.clinic.repository.StockLedgerRepository;
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

/**
 * 库存：入库、盘点、发药扣减与流水。
 * 所有数量变动都写 stock_ledger，结存以批次的 quantity 为准。
 */
@Service
public class StockService {

    private final DrugRepository drugRepository;
    private final DrugBatchRepository batchRepository;
    private final StockLedgerRepository ledgerRepository;

    public StockService(DrugRepository drugRepository,
                        DrugBatchRepository batchRepository,
                        StockLedgerRepository ledgerRepository) {
        this.drugRepository = drugRepository;
        this.batchRepository = batchRepository;
        this.ledgerRepository = ledgerRepository;
    }

    public BigDecimal stockOf(Long drugId) {
        BigDecimal total = batchRepository.sumQuantityByDrugId(drugId);
        return total == null ? BigDecimal.ZERO : total;
    }

    public Map<String, Object> stockDetail(Long drugId) {
        Drug drug = drugRepository.findById(drugId)
                .orElseThrow(() -> BusinessException.notFound("药品"));
        List<DrugBatch> batches = batchRepository.findByDrugIdOrderByExpiryDateAsc(drugId);
        List<Map<String, Object>> batchRows = new ArrayList<>();
        for (DrugBatch batch : batches) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("batchNo", batch.getBatchNo());
            row.put("productionDate", batch.getProductionDate());
            row.put("expiryDate", batch.getExpiryDate());
            row.put("quantity", batch.getQuantity());
            batchRows.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("drug", new DrugDto(drug.getId(), drug.getCode(), drug.getName(), drug.getPinyin(),
                drug.getCategory(), drug.getSpec(), drug.getUnit(), drug.getPrice(), drug.getCostPrice(),
                drug.getStockWarn(), drug.getStockMax(), stockOf(drugId), drug.getNeedTrace(),
                drug.getSpecialHandling(), drug.getDoseMin(), drug.getDoseMax(), drug.getPregnancyLevel(),
                drug.getToxicityLevel(), drug.getFunctions(), drug.getStatus()));
        data.put("totalQty", stockOf(drugId));
        data.put("batches", batchRows);
        return data;
    }

    @Transactional
    public Map<String, Object> stockIn(Long drugId, String batchNo, LocalDate productionDate,
                                       LocalDate expiryDate, BigDecimal quantity,
                                       BigDecimal costPrice, String remark) {
        Drug drug = drugRepository.findById(drugId)
                .orElseThrow(() -> BusinessException.notFound("药品"));
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("入库数量必须大于 0");
        }
        if (batchNo == null || batchNo.isBlank()) {
            throw new BusinessException("请填写批号");
        }
        DrugBatch batch = batchRepository.findByDrugIdAndBatchNo(drugId, batchNo).orElseGet(() -> {
            DrugBatch created = new DrugBatch();
            created.setDrugId(drugId);
            created.setBatchNo(batchNo);
            created.setQuantity(BigDecimal.ZERO);
            return created;
        });
        BigDecimal before = batch.getQuantity();
        batch.setProductionDate(productionDate);
        batch.setExpiryDate(expiryDate);
        if (costPrice != null) {
            batch.setCostPrice(costPrice);
        }
        batch.setQuantity(before.add(quantity));
        batchRepository.save(batch);

        writeLedger(drug, batchNo, StockChangeType.IN, quantity, before, batch.getQuantity(),
                "MANUAL", null, remark);
        return stockDetail(drugId);
    }

    /** 盘点：把指定批次的数量调整为目标值。 */
    @Transactional
    public Map<String, Object> adjust(Long drugId, String batchNo, BigDecimal targetQuantity, String remark) {
        Drug drug = drugRepository.findById(drugId)
                .orElseThrow(() -> BusinessException.notFound("药品"));
        if (targetQuantity == null || targetQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("盘点数量不能为负数");
        }
        DrugBatch batch = batchRepository.findByDrugIdAndBatchNo(drugId, batchNo)
                .orElseThrow(() -> new BusinessException("该批次不存在，请先入库"));
        BigDecimal before = batch.getQuantity();
        BigDecimal diff = targetQuantity.subtract(before);
        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            return stockDetail(drugId);
        }
        batch.setQuantity(targetQuantity);
        batchRepository.save(batch);
        writeLedger(drug, batchNo, StockChangeType.ADJUST, diff, before, targetQuantity,
                "MANUAL", null, remark);
        return stockDetail(drugId);
    }

    /**
     * 发药扣减库存：按效期先进先出，跨批次扣减。
     */
    @Transactional
    public void deduct(Long drugId, BigDecimal quantity, String refType, Long refId) {
        deduct(drugId, quantity, refType, refId, "发药出库");
    }

    /** 出库并写自定义流水备注（扫码直接出库等场景用）。 */
    @Transactional
    public void deduct(Long drugId, BigDecimal quantity, String refType, Long refId, String remark) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Drug drug = drugRepository.findById(drugId)
                .orElseThrow(() -> BusinessException.notFound("药品"));
        List<DrugBatch> batches = batchRepository.findAvailableBatches(drugId);
        BigDecimal available = batches.stream()
                .map(DrugBatch::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(quantity) < 0) {
            throw new BusinessException("药品「" + drug.getName() + "」库存不足，当前库存 "
                    + available.stripTrailingZeros().toPlainString() + " " + drug.getUnit());
        }
        BigDecimal remaining = quantity;
        for (DrugBatch batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal take = batch.getQuantity().min(remaining);
            BigDecimal before = batch.getQuantity();
            batch.setQuantity(before.subtract(take));
            batchRepository.save(batch);
            writeLedger(drug, batch.getBatchNo(), StockChangeType.OUT, take.negate(), before,
                    batch.getQuantity(), refType, refId, remark);
            remaining = remaining.subtract(take);
        }
    }

    private void writeLedger(Drug drug, String batchNo, StockChangeType type, BigDecimal change,
                             BigDecimal before, BigDecimal after, String refType, Long refId, String remark) {
        StockLedger ledger = new StockLedger();
        ledger.setDrugId(drug.getId());
        ledger.setDrugName(drug.getName());
        ledger.setBatchNo(batchNo);
        ledger.setChangeType(type);
        ledger.setQuantity(change);
        ledger.setBeforeQty(before);
        ledger.setAfterQty(after);
        ledger.setRefType(refType);
        ledger.setRefId(refId);
        ledger.setOperatorId(SecurityUtils.currentUserId());
        ledger.setOperatorName(SecurityUtils.currentUser().realName());
        ledger.setRemark(remark);
        ledgerRepository.save(ledger);
    }

    public PageResult<LedgerItem> ledger(Long drugId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<StockLedger> result = drugId == null
                ? ledgerRepository.findAllByOrderByIdDesc(pageable)
                : ledgerRepository.findByDrugIdOrderByIdDesc(drugId, pageable);
        return PageResult.of(result, ledger -> new LedgerItem(ledger.getId(), ledger.getDrugId(),
                ledger.getDrugName(), ledger.getBatchNo(), ledger.getChangeType(), ledger.getQuantity(),
                ledger.getBeforeQty(), ledger.getAfterQty(), ledger.getOperatorName(), ledger.getRemark(),
                ledger.getCreatedAt()));
    }

    /** 低库存与临期告警。 */
    public Map<String, Object> alerts() {
        List<DrugDto> lowStock = new ArrayList<>();
        for (Drug drug : drugRepository.findAll()) {
            BigDecimal stock = stockOf(drug.getId());
            BigDecimal warn = drug.getStockWarn() == null ? BigDecimal.ZERO : drug.getStockWarn();
            if (stock.compareTo(warn) <= 0) {
                lowStock.add(new DrugDto(drug.getId(), drug.getCode(), drug.getName(), drug.getPinyin(),
                        drug.getCategory(), drug.getSpec(), drug.getUnit(), drug.getPrice(),
                        drug.getCostPrice(), drug.getStockWarn(), drug.getStockMax(), stock,
                        drug.getNeedTrace(), drug.getSpecialHandling(), drug.getDoseMin(), drug.getDoseMax(),
                        drug.getPregnancyLevel(), drug.getToxicityLevel(), drug.getFunctions(), drug.getStatus()));
            }
        }
        List<Map<String, Object>> expiring = new ArrayList<>();
        java.time.LocalDate before = java.time.LocalDate.now().plusDays(90);
        for (DrugBatch batch : batchRepository.findExpiringBefore(before)) {
            String drugName = drugRepository.findById(batch.getDrugId())
                    .map(Drug::getName).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("drugId", batch.getDrugId());
            row.put("drugName", drugName);
            row.put("batchNo", batch.getBatchNo());
            row.put("expiryDate", batch.getExpiryDate());
            row.put("quantity", batch.getQuantity());
            expiring.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lowStock", lowStock);
        data.put("expiring", expiring);
        return data;
    }

    /** 库存流水视图。 */
    public record LedgerItem(Long id, Long drugId, String drugName, String batchNo,
                             StockChangeType changeType, BigDecimal quantity, BigDecimal beforeQty,
                             BigDecimal afterQty, String operatorName, String remark,
                             LocalDateTime createdAt) {
    }

    /** 供报表使用的日期边界。 */
    public static LocalDateTime startOf(LocalDate date) {
        return date.atStartOfDay();
    }
}
