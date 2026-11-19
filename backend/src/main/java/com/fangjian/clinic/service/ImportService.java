package com.fangjian.clinic.service;

import com.fangjian.clinic.common.BusinessException;
import com.fangjian.clinic.common.CodeGenerator;
import com.fangjian.clinic.domain.Drug;
import com.fangjian.clinic.domain.MedicalRecord;
import com.fangjian.clinic.domain.Patient;
import com.fangjian.clinic.domain.SysUser;
import com.fangjian.clinic.domain.Visit;
import com.fangjian.clinic.domain.enums.DrugCategory;
import com.fangjian.clinic.domain.enums.Gender;
import com.fangjian.clinic.domain.enums.ImportType;
import com.fangjian.clinic.domain.enums.PregnancyLevel;
import com.fangjian.clinic.domain.enums.RecordType;
import com.fangjian.clinic.domain.enums.SpecialHandling;
import com.fangjian.clinic.domain.enums.ToxicityLevel;
import com.fangjian.clinic.domain.enums.UserRole;
import com.fangjian.clinic.domain.enums.VisitStatus;
import com.fangjian.clinic.domain.enums.VisitType;
import com.fangjian.clinic.dto.ImportDtos;
import com.fangjian.clinic.repository.DrugRepository;
import com.fangjian.clinic.repository.MedicalRecordRepository;
import com.fangjian.clinic.repository.PatientRepository;
import com.fangjian.clinic.repository.SysUserRepository;
import com.fangjian.clinic.repository.VisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Excel 数据迁移：患者档案、药品字典、初始库存、历史病历。
 *
 * <p>设计原则：
 * <ul>
 *   <li>先预览后入库——脏数据不进库；</li>
 *   <li>逐行独立事务——一行失败不影响其他行，失败原因回传；</li>
 *   <li>有匹配键就跳过或更新，避免重复导入产生脏数据；</li>
 *   <li>库存导入写库存流水，不直接改结存，保证账实可追。</li>
 * </ul>
 */
@Service
public class ImportService {

    public static final String MODE_CREATE_ONLY = "CREATE_ONLY";
    public static final String MODE_UPSERT = "UPSERT";
    private static final int MAX_ERRORS = 50;

    private final ExcelService excelService;
    private final PatientRepository patientRepository;
    private final DrugRepository drugRepository;
    private final VisitRepository visitRepository;
    private final MedicalRecordRepository recordRepository;
    private final SysUserRepository userRepository;
    private final StockService stockService;
    private final TransactionTemplate transactionTemplate;

    public ImportService(ExcelService excelService,
                         PatientRepository patientRepository,
                         DrugRepository drugRepository,
                         VisitRepository visitRepository,
                         MedicalRecordRepository recordRepository,
                         SysUserRepository userRepository,
                         StockService stockService,
                         TransactionTemplate transactionTemplate) {
        this.excelService = excelService;
        this.patientRepository = patientRepository;
        this.drugRepository = drugRepository;
        this.visitRepository = visitRepository;
        this.recordRepository = recordRepository;
        this.userRepository = userRepository;
        this.stockService = stockService;
        this.transactionTemplate = transactionTemplate;
    }

    /* ---------------- 模板定义 ---------------- */

    private static final Map<ImportType, List<String>> HEADERS = new LinkedHashMap<>();
    private static final Map<ImportType, String> INSTRUCTIONS = new LinkedHashMap<>();
    private static final Map<ImportType, List<String>> EXAMPLES = new LinkedHashMap<>();

    static {
        HEADERS.put(ImportType.PATIENT, List.of("姓名*", "性别*", "出生日期", "手机号*", "身份证号", "地址", "过敏史", "既往史"));
        INSTRUCTIONS.put(ImportType.PATIENT, "性别填「男」或「女」；出生日期格式 yyyy-MM-dd；手机号+姓名作为匹配键，已存在则按导入模式处理");
        EXAMPLES.put(ImportType.PATIENT, List.of("张三", "男", "1975-03-12", "13800000001", "330102197503120011", "示例市示例路 1 号", "青霉素过敏", "高血压 5 年"));

        HEADERS.put(ImportType.DRUG, List.of("药品编码*", "名称*", "拼音码", "分类*", "规格", "单位*", "售价*", "进价",
                "库存预警值", "库存上限", "需追溯码", "特殊煎法", "剂量下限", "剂量上限", "妊娠风险", "毒性", "功效"));
        INSTRUCTIONS.put(ImportType.DRUG, "分类填「中药饮片/中成药/西药/耗材」；需追溯码填「是/否」；"
                + "特殊煎法填「常规/先煎/后下/包煎/另煎/烊化/冲服」；妊娠风险填「无/慎用/禁用」；毒性填「无/轻微/毒性」");
        EXAMPLES.put(ImportType.DRUG, List.of("H900", "黄芪", "HQ", "中药饮片", "饮片", "g", "0.35", "0.25",
                "2000", "20000", "否", "常规", "9", "30", "无", "无", "补气升阳"));

        HEADERS.put(ImportType.STOCK, List.of("药品编码*", "批号*", "生产日期", "有效期", "数量*", "进价", "备注"));
        INSTRUCTIONS.put(ImportType.STOCK, "药品编码须先在药品字典中存在；日期格式 yyyy-MM-dd；数量为本次入库数量（会写库存流水）");
        EXAMPLES.put(ImportType.STOCK, List.of("H900", "B2026001", "2026-01-01", "2027-12-31", "5000", "0.25", "初始化库存"));

        HEADERS.put(ImportType.RECORD, List.of("患者手机号*", "就诊日期*", "医生姓名*", "主诉*", "现病史*", "既往史",
                "望诊", "闻诊", "问诊", "切诊", "舌象", "脉象", "中医病名", "证候", "治则治法", "医嘱"));
        INSTRUCTIONS.put(ImportType.RECORD, "患者须已建档（手机号匹配）；医生姓名须与员工账号姓名一致；"
                + "就诊日期格式 yyyy-MM-dd；导入会生成「已完成」的历史就诊记录，不影响当日业务");
        EXAMPLES.put(ImportType.RECORD, List.of("13800000001", "2026-10-15", "李青囊", "乏力纳差 2 周",
                "患者 2 周前出现乏力，纳差，餐后腹胀。", "高血压 3 年", "面色萎黄", "语声低微", "纳差，眠可",
                "腹软无压痛", "舌淡红，苔薄白", "脉细弱", "虚劳", "脾胃气虚证", "健脾益气，和胃消食", "忌生冷油腻"));
    }

    public byte[] template(ImportType type) {
        return excelService.template(type.name(), HEADERS.get(type), INSTRUCTIONS.get(type), EXAMPLES.get(type));
    }

    /* ---------------- 预览 ---------------- */

    public ImportDtos.PreviewResult preview(ImportType type, MultipartFile file) {
        List<Map<String, String>> rows = excelService.read(file);
        List<ImportDtos.RowError> errors = new ArrayList<>();
        int valid = 0;
        List<Map<String, String>> sample = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2;
            Parsed parsed = parse(type, rowNumber, rows.get(i));
            if (parsed.errors().isEmpty()) {
                valid++;
                if (sample.size() < 5) {
                    sample.add(rows.get(i));
                }
            } else {
                errors.addAll(parsed.errors());
            }
        }
        return new ImportDtos.PreviewResult(type.name(), rows.size(), valid, rows.size() - valid,
                limit(errors), sample);
    }

    /* ---------------- 执行 ---------------- */

    public ImportDtos.ExecuteResult execute(ImportType type, MultipartFile file, String mode) {
        List<Map<String, String>> rows = excelService.read(file);
        boolean upsert = MODE_UPSERT.equalsIgnoreCase(mode);
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        List<ImportDtos.RowError> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2;
            Parsed parsed = parse(type, rowNumber, rows.get(i));
            if (!parsed.errors().isEmpty()) {
                failed++;
                errors.addAll(parsed.errors());
                continue;
            }
            try {
                String outcome = transactionTemplate.execute(status -> apply(parsed, upsert));
                if ("INSERT".equals(outcome)) {
                    inserted++;
                } else if ("UPDATE".equals(outcome)) {
                    updated++;
                } else {
                    skipped++;
                }
            } catch (Exception ex) {
                failed++;
                errors.add(new ImportDtos.RowError(rowNumber, "-", ex.getMessage()));
            }
        }
        return new ImportDtos.ExecuteResult(type.name(), inserted, updated, skipped, failed, limit(errors));
    }

    private List<ImportDtos.RowError> limit(List<ImportDtos.RowError> errors) {
        return errors.size() <= MAX_ERRORS ? errors : new ArrayList<>(errors.subList(0, MAX_ERRORS));
    }

    /* ---------------- 解析与校验 ---------------- */

    @SuppressWarnings("unchecked")
    private Parsed parse(ImportType type, int rowNumber, Map<String, String> raw) {
        List<ImportDtos.RowError> errors = new ArrayList<>();
        Object data = switch (type) {
            case PATIENT -> parsePatient(rowNumber, raw, errors);
            case DRUG -> parseDrug(rowNumber, raw, errors);
            case STOCK -> parseStock(rowNumber, raw, errors);
            case RECORD -> parseRecord(rowNumber, raw, errors);
        };
        return new Parsed(rowNumber, data, errors);
    }

    private PatientPayload parsePatient(int row, Map<String, String> raw, List<ImportDtos.RowError> errors) {
        String name = required(raw, "姓名", row, errors);
        String genderText = required(raw, "性别", row, errors);
        String phone = required(raw, "手机号", row, errors);
        Gender gender = null;
        if (genderText != null) {
            gender = switch (genderText) {
                case "男" -> Gender.MALE;
                case "女" -> Gender.FEMALE;
                default -> {
                    errors.add(new ImportDtos.RowError(row, "性别", "只能填「男」或「女」"));
                    yield null;
                }
            };
        }
        LocalDate birth = date(raw.get("出生日期"), "出生日期", row, errors);
        if (phone != null && !phone.matches("1\\d{10}")) {
            errors.add(new ImportDtos.RowError(row, "手机号", "手机号格式不正确（11 位）"));
        }
        return new PatientPayload(name, gender, birth, phone, raw.get("身份证号"), raw.get("地址"),
                raw.get("过敏史"), raw.get("既往史"));
    }

    private DrugPayload parseDrug(int row, Map<String, String> raw, List<ImportDtos.RowError> errors) {
        String code = required(raw, "药品编码", row, errors);
        String name = required(raw, "名称", row, errors);
        String categoryText = required(raw, "分类", row, errors);
        String unit = required(raw, "单位", row, errors);
        BigDecimal price = decimal(raw.get("售价"), "售价", row, errors, true);
        DrugCategory category = null;
        if (categoryText != null) {
            category = switch (categoryText) {
                case "中药饮片" -> DrugCategory.HERB;
                case "中成药" -> DrugCategory.PATENT;
                case "西药" -> DrugCategory.WESTERN;
                case "耗材" -> DrugCategory.MATERIAL;
                default -> {
                    errors.add(new ImportDtos.RowError(row, "分类", "只能填 中药饮片/中成药/西药/耗材"));
                    yield null;
                }
            };
        }
        SpecialHandling handling = switch (orEmpty(raw.get("特殊煎法"))) {
            case "先煎" -> SpecialHandling.PRE_DECOCT;
            case "后下" -> SpecialHandling.POST_ADD;
            case "包煎" -> SpecialHandling.WRAP_DECOCT;
            case "另煎" -> SpecialHandling.SEPARATE_DECOCT;
            case "烊化" -> SpecialHandling.MELT;
            case "冲服" -> SpecialHandling.TAKE_WITH;
            default -> SpecialHandling.NORMAL;
        };
        PregnancyLevel pregnancy = switch (orEmpty(raw.get("妊娠风险"))) {
            case "慎用" -> PregnancyLevel.CAUTION;
            case "禁用" -> PregnancyLevel.FORBIDDEN;
            default -> PregnancyLevel.NONE;
        };
        ToxicityLevel toxicity = switch (orEmpty(raw.get("毒性"))) {
            case "轻微" -> ToxicityLevel.MILD;
            case "毒性" -> ToxicityLevel.TOXIC;
            default -> ToxicityLevel.NONE;
        };
        BigDecimal doseMin = decimal(raw.get("剂量下限"), "剂量下限", row, errors, false);
        BigDecimal doseMax = decimal(raw.get("剂量上限"), "剂量上限", row, errors, false);
        if (doseMin != null && doseMax != null && doseMin.compareTo(doseMax) > 0) {
            errors.add(new ImportDtos.RowError(row, "剂量范围", "剂量下限不能大于上限"));
        }
        boolean needTrace = "是".equals(orEmpty(raw.get("需追溯码")));
        return new DrugPayload(code, name, raw.get("拼音码"), category, raw.get("规格"), unit, price,
                decimal(raw.get("进价"), "进价", row, errors, false),
                decimal(raw.get("库存预警值"), "库存预警值", row, errors, false),
                decimal(raw.get("库存上限"), "库存上限", row, errors, false),
                needTrace, handling, doseMin, doseMax, pregnancy, toxicity, raw.get("功效"));
    }

    private StockPayload parseStock(int row, Map<String, String> raw, List<ImportDtos.RowError> errors) {
        String code = required(raw, "药品编码", row, errors);
        String batchNo = required(raw, "批号", row, errors);
        BigDecimal quantity = decimal(raw.get("数量"), "数量", row, errors, true);
        if (quantity != null && quantity.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new ImportDtos.RowError(row, "数量", "数量必须大于 0"));
        }
        return new StockPayload(code, batchNo, date(raw.get("生产日期"), "生产日期", row, errors),
                date(raw.get("有效期"), "有效期", row, errors), quantity,
                decimal(raw.get("进价"), "进价", row, errors, false), raw.get("备注"));
    }

    private RecordPayload parseRecord(int row, Map<String, String> raw, List<ImportDtos.RowError> errors) {
        String phone = required(raw, "患者手机号", row, errors);
        LocalDate visitDate = date(required(raw, "就诊日期", row, errors), "就诊日期", row, errors, true);
        String doctorName = required(raw, "医生姓名", row, errors);
        String chief = required(raw, "主诉", row, errors);
        String present = required(raw, "现病史", row, errors);
        return new RecordPayload(phone, visitDate, doctorName, chief, present, raw.get("既往史"),
                raw.get("望诊"), raw.get("闻诊"), raw.get("问诊"), raw.get("切诊"),
                raw.get("舌象"), raw.get("脉象"), raw.get("中医病名"), raw.get("证候"),
                raw.get("治则治法"), raw.get("医嘱"));
    }

    /* ---------------- 落库 ---------------- */

    private String apply(Parsed parsed, boolean upsert) {
        Object data = parsed.data();
        if (data instanceof PatientPayload payload) {
            return applyPatient(payload, upsert);
        }
        if (data instanceof DrugPayload payload) {
            return applyDrug(payload, upsert);
        }
        if (data instanceof StockPayload payload) {
            return applyStock(payload);
        }
        if (data instanceof RecordPayload payload) {
            return applyRecord(payload, upsert);
        }
        throw new BusinessException("未知的导入类型");
    }

    private String applyPatient(PatientPayload payload, boolean upsert) {
        Optional<Patient> existing = patientRepository.findByPhone(payload.phone()).stream()
                .filter(p -> p.getName().equals(payload.name())).findFirst();
        if (existing.isPresent()) {
            if (!upsert) {
                return "SKIP";
            }
            Patient patient = existing.get();
            patient.setGender(payload.gender());
            patient.setBirthDate(payload.birthDate());
            patient.setIdCard(payload.idCard());
            patient.setAddress(payload.address());
            patient.setAllergy(payload.allergy());
            patient.setChronicHistory(payload.chronicHistory());
            patientRepository.save(patient);
            return "UPDATE";
        }
        Patient patient = new Patient();
        patient.setPatientNo(CodeGenerator.dateCode("P"));
        patient.setName(payload.name());
        patient.setGender(payload.gender());
        patient.setBirthDate(payload.birthDate());
        patient.setPhone(payload.phone());
        patient.setIdCard(payload.idCard());
        patient.setAddress(payload.address());
        patient.setAllergy(payload.allergy());
        patient.setChronicHistory(payload.chronicHistory());
        patientRepository.save(patient);
        return "INSERT";
    }

    private String applyDrug(DrugPayload payload, boolean upsert) {
        Optional<Drug> existing = drugRepository.findAll().stream()
                .filter(d -> d.getCode().equals(payload.code())).findFirst();
        Drug drug = existing.orElseGet(Drug::new);
        boolean isNew = existing.isEmpty();
        if (!isNew && !upsert) {
            return "SKIP";
        }
        drug.setCode(payload.code());
        drug.setName(payload.name());
        drug.setPinyin(payload.pinyin());
        drug.setCategory(payload.category());
        drug.setSpec(payload.spec());
        drug.setUnit(payload.unit());
        drug.setPrice(payload.price());
        drug.setCostPrice(defaultZero(payload.costPrice()));
        drug.setStockWarn(defaultZero(payload.stockWarn()));
        drug.setStockMax(defaultZero(payload.stockMax()));
        drug.setNeedTrace(payload.needTrace() ? 1 : 0);
        drug.setSpecialHandling(payload.handling());
        drug.setDoseMin(payload.doseMin());
        drug.setDoseMax(payload.doseMax());
        drug.setPregnancyLevel(payload.pregnancy());
        drug.setToxicityLevel(payload.toxicity());
        drug.setFunctions(payload.functions());
        if (isNew) {
            drug.setStatus(1);
        }
        drugRepository.save(drug);
        return isNew ? "INSERT" : "UPDATE";
    }

    private String applyStock(StockPayload payload) {
        Drug drug = drugRepository.findAll().stream()
                .filter(d -> d.getCode().equals(payload.drugCode())).findFirst()
                .orElseThrow(() -> new BusinessException("药品编码 " + payload.drugCode() + " 不存在，请先导入药品字典"));
        stockService.stockIn(drug.getId(), payload.batchNo(), payload.productionDate(),
                payload.expiryDate(), payload.quantity(), payload.costPrice(),
                StringUtils.hasText(payload.remark()) ? payload.remark() : "Excel 导入");
        return "INSERT";
    }

    private String applyRecord(RecordPayload payload, boolean upsert) {
        Patient patient = patientRepository.findByPhone(payload.phone()).stream().findFirst()
                .orElseThrow(() -> new BusinessException("患者手机号 " + payload.phone() + " 未建档，请先导入患者档案"));
        SysUser doctor = userRepository.findByRoleAndStatusOrderByRealNameAsc(UserRole.DOCTOR, 1).stream()
                .filter(u -> u.getRealName().equals(payload.doctorName())).findFirst()
                .orElseThrow(() -> new BusinessException("医生「" + payload.doctorName() + "」不存在或已停用"));

        LocalDateTime from = payload.visitDate().atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        List<Visit> exists = visitRepository.findByPatientIdAndRegisterTimeBetweenOrderByIdAsc(
                patient.getId(), from, to);
        if (!exists.isEmpty()) {
            if (!upsert) {
                return "SKIP";
            }
            Visit visit = exists.get(0);
            MedicalRecord record = recordRepository.findByVisitId(visit.getId()).orElseGet(() -> {
                MedicalRecord created = new MedicalRecord();
                created.setVisitId(visit.getId());
                created.setVersion(0);
                return created;
            });
            fillRecord(record, payload);
            recordRepository.save(record);
            return "UPDATE";
        }

        Visit visit = new Visit();
        visit.setVisitNo(CodeGenerator.timeCode("V"));
        visit.setPatientId(patient.getId());
        visit.setDoctorId(doctor.getId());
        visit.setType(VisitType.FOLLOWUP);
        visit.setStatus(VisitStatus.FINISHED);
        visit.setRegisterTime(LocalDateTime.of(payload.visitDate(), LocalTime.of(9, 0)));
        visit.setStartTime(LocalDateTime.of(payload.visitDate(), LocalTime.of(9, 0)));
        visit.setEndTime(LocalDateTime.of(payload.visitDate(), LocalTime.of(9, 30)));
        visit.setRegisterFee(BigDecimal.ZERO);
        visit.setComplaint(payload.chiefComplaint());
        visitRepository.save(visit);

        MedicalRecord record = new MedicalRecord();
        record.setVisitId(visit.getId());
        record.setVersion(0);
        fillRecord(record, payload);
        recordRepository.save(record);
        return "INSERT";
    }

    private void fillRecord(MedicalRecord record, RecordPayload payload) {
        record.setRecordType(RecordType.TCM);
        record.setChiefComplaint(payload.chiefComplaint());
        record.setPresentIllness(payload.presentIllness());
        record.setPastHistory(payload.pastHistory());
        record.setInspection(payload.inspection());
        record.setAuscultation(payload.auscultation());
        record.setInquiry(payload.inquiry());
        record.setPalpation(payload.palpation());
        record.setTongue(payload.tongue());
        record.setPulse(payload.pulse());
        record.setTcmDisease(payload.tcmDisease());
        record.setSyndrome(payload.syndrome());
        record.setTreatmentPrinciple(payload.treatmentPrinciple());
        record.setAdvice(payload.advice());
        record.setVersion(record.getVersion() == null ? 1 : record.getVersion() + 1);
    }

    /* ---------------- 工具 ---------------- */

    private String required(Map<String, String> raw, String key, int row, List<ImportDtos.RowError> errors) {
        String value = raw.get(key);
        if (!StringUtils.hasText(value)) {
            errors.add(new ImportDtos.RowError(row, key, "必填项为空"));
            return null;
        }
        return value.trim();
    }

    private BigDecimal decimal(String value, String field, int row, List<ImportDtos.RowError> errors,
                               boolean required) {
        if (!StringUtils.hasText(value)) {
            if (required) {
                errors.add(new ImportDtos.RowError(row, field, "必填项为空"));
            }
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            errors.add(new ImportDtos.RowError(row, field, "不是有效数字：" + value));
            return null;
        }
    }

    private LocalDate date(String value, String field, int row, List<ImportDtos.RowError> errors) {
        return date(value, field, row, errors, false);
    }

    private LocalDate date(String value, String field, int row, List<ImportDtos.RowError> errors, boolean required) {
        if (!StringUtils.hasText(value)) {
            if (required) {
                errors.add(new ImportDtos.RowError(row, field, "必填项为空"));
            }
            return null;
        }
        try {
            return LocalDate.parse(value.trim().replace('/', '-'));
        } catch (Exception ex) {
            errors.add(new ImportDtos.RowError(row, field, "日期格式应为 yyyy-MM-dd：" + value));
            return null;
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /* ---------------- 内部载荷 ---------------- */

    private record Parsed(int row, Object data, List<ImportDtos.RowError> errors) {
    }

    private record PatientPayload(String name, Gender gender, LocalDate birthDate, String phone,
                                  String idCard, String address, String allergy, String chronicHistory) {
    }

    private record DrugPayload(String code, String name, String pinyin, DrugCategory category, String spec,
                               String unit, BigDecimal price, BigDecimal costPrice, BigDecimal stockWarn,
                               BigDecimal stockMax, boolean needTrace, SpecialHandling handling,
                               BigDecimal doseMin, BigDecimal doseMax, PregnancyLevel pregnancy,
                               ToxicityLevel toxicity, String functions) {
    }

    private record StockPayload(String drugCode, String batchNo, LocalDate productionDate,
                               LocalDate expiryDate, BigDecimal quantity, BigDecimal costPrice, String remark) {
    }

    private record RecordPayload(String phone, LocalDate visitDate, String doctorName, String chiefComplaint,
                                 String presentIllness, String pastHistory, String inspection,
                                 String auscultation, String inquiry, String palpation, String tongue,
                                 String pulse, String tcmDisease, String syndrome,
                                 String treatmentPrinciple, String advice) {
    }
}
