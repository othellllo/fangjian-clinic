package com.yiguan.his.service;

import com.yiguan.his.common.BusinessException;
import com.yiguan.his.common.PageResult;
import com.yiguan.his.domain.Drug;
import com.yiguan.his.domain.enums.DrugCategory;
import com.yiguan.his.dto.DrugDto;
import com.yiguan.his.repository.DrugRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/** 药品字典。 */
@Service
public class DrugService {

    private final DrugRepository drugRepository;
    private final StockService stockService;

    public DrugService(DrugRepository drugRepository, StockService stockService) {
        this.drugRepository = drugRepository;
        this.stockService = stockService;
    }

    public PageResult<DrugDto> page(String keyword, DrugCategory category, int page, int size) {
        Page<Drug> result = drugRepository.search(keyword == null ? "" : keyword.trim(), category, null,
                PageRequest.of(Math.max(page - 1, 0), size));
        return PageResult.of(result, this::toDto);
    }

    public DrugDto get(Long id) {
        return toDto(find(id));
    }

    public Drug find(Long id) {
        return drugRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("药品"));
    }

    public DrugDto toDto(Drug drug) {
        return new DrugDto(drug.getId(), drug.getCode(), drug.getName(), drug.getPinyin(), drug.getCategory(),
                drug.getSpec(), drug.getUnit(), drug.getPrice(), drug.getCostPrice(), drug.getStockWarn(),
                drug.getStockMax(), stockService.stockOf(drug.getId()), drug.getNeedTrace(),
                drug.getSpecialHandling(), drug.getDoseMin(), drug.getDoseMax(), drug.getPregnancyLevel(),
                drug.getToxicityLevel(), drug.getFunctions(), drug.getStatus());
    }

    @Transactional
    public DrugDto create(String code, String name, String pinyin, DrugCategory category, String spec,
                          String unit, BigDecimal price, BigDecimal costPrice, BigDecimal stockWarn) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
            throw new BusinessException("药品编码和名称不能为空");
        }
        if (drugRepository.existsByCode(code)) {
            throw new BusinessException("药品编码已存在");
        }
        Drug drug = new Drug();
        drug.setCode(code.trim());
        drug.setName(name.trim());
        drug.setPinyin(pinyin);
        drug.setCategory(category == null ? DrugCategory.HERB : category);
        drug.setSpec(spec);
        drug.setUnit(StringUtils.hasText(unit) ? unit : "g");
        drug.setPrice(price == null ? BigDecimal.ZERO : price);
        drug.setCostPrice(costPrice == null ? BigDecimal.ZERO : costPrice);
        drug.setStockWarn(stockWarn == null ? BigDecimal.ZERO : stockWarn);
        // 追溯码采集范围：中成药/西药/耗材需要，中药饮片暂不强制（国家医保局 2024-09-30 公告）
        drug.setNeedTrace(drug.getCategory() == DrugCategory.HERB ? 0 : 1);
        drug.setStatus(1);
        return toDto(drugRepository.save(drug));
    }

    /** 维护临床属性：煎法、剂量范围、妊娠风险、毒性、功效。 */
    @Transactional
    public DrugDto updateClinical(Long id, com.yiguan.his.domain.enums.SpecialHandling specialHandling,
                                  java.math.BigDecimal doseMin, java.math.BigDecimal doseMax,
                                  com.yiguan.his.domain.enums.PregnancyLevel pregnancyLevel,
                                  com.yiguan.his.domain.enums.ToxicityLevel toxicityLevel, String functions) {
        Drug drug = find(id);
        if (specialHandling != null) {
            drug.setSpecialHandling(specialHandling);
        }
        drug.setDoseMin(doseMin);
        drug.setDoseMax(doseMax);
        if (pregnancyLevel != null) {
            drug.setPregnancyLevel(pregnancyLevel);
        }
        if (toxicityLevel != null) {
            drug.setToxicityLevel(toxicityLevel);
        }
        drug.setFunctions(functions);
        return toDto(drugRepository.save(drug));
    }

    @Transactional
    public DrugDto update(Long id, String name, String pinyin, DrugCategory category, String spec,
                          String unit, BigDecimal price, BigDecimal costPrice, BigDecimal stockWarn, Integer status) {
        Drug drug = find(id);
        if (StringUtils.hasText(name)) {
            drug.setName(name.trim());
        }
        drug.setPinyin(pinyin);
        if (category != null) {
            drug.setCategory(category);
        }
        drug.setSpec(spec);
        if (StringUtils.hasText(unit)) {
            drug.setUnit(unit);
        }
        if (price != null) {
            drug.setPrice(price);
        }
        if (costPrice != null) {
            drug.setCostPrice(costPrice);
        }
        if (stockWarn != null) {
            drug.setStockWarn(stockWarn);
        }
        if (status != null) {
            drug.setStatus(status);
        }
        return toDto(drugRepository.save(drug));
    }
}
