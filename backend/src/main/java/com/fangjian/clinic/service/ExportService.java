package com.fangjian.clinic.service;

import com.fangjian.clinic.common.BusinessException;
import com.fangjian.clinic.security.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据导出（CSV）。
 *
 * <p>选择 CSV 而不是 Excel：任何机子上 Excel/WPS 都能直接打开，用户不被系统锁死；
 * 加 UTF-8 BOM 避免中文乱码。导出行为会被审计过滤器记录（GET 不走审计，
 * 因此这里显式记一条，保证"谁导出了什么数据"可追溯）。</p>
 */
@Service
public class ExportService {

    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public ExportService(JdbcTemplate jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public byte[] export(String type, LocalDate start, LocalDate end) {
        String normalized = type == null ? "" : type.toLowerCase();
        LocalDateTime from = start == null ? null : start.atStartOfDay();
        LocalDateTime to = end == null ? null : end.plusDays(1).atStartOfDay();

        String sql;
        String dateColumn;
        switch (normalized) {
            case "patients" -> {
                dateColumn = "created_at";
                sql = """
                        select patient_no as 病历号, name as 姓名, gender as 性别, birth_date as 出生日期,
                               phone as 手机号, id_card as 身份证, address as 地址, allergy as 过敏史,
                               chronic_history as 既往史, created_at as 建档时间
                        from patient
                        """;
            }
            case "visits" -> {
                dateColumn = "v.register_time";
                sql = """
                        select v.visit_no as 就诊号, p.name as 患者, u.real_name as 医生, v.type as 类型,
                               v.status as 状态, v.queue_no as 排队号, v.register_fee as 挂号费,
                               v.register_time as 挂号时间, v.complaint as 主诉
                        from visit v left join patient p on p.id = v.patient_id
                        left join sys_user u on u.id = v.doctor_id
                        """;
            }
            case "charges" -> {
                dateColumn = "c.created_at";
                sql = """
                        select c.order_no as 单号, p.name as 患者, c.type as 类型, c.total_amount as 应收,
                               c.paid_amount as 实收, c.status as 状态, c.pay_method as 支付方式,
                               c.created_at as 创建时间, c.paid_at as 收费时间
                        from charge_order c left join patient p on p.id = c.patient_id
                        """;
            }
            case "prescriptions" -> {
                dateColumn = "r.created_at";
                sql = """
                        select r.prescription_no as 处方号, p.name as 患者, u.real_name as 医生,
                               r.type as 类型, r.decoction_method as 煎法, r.doses as 剂数,
                               r.total_amount as 金额, r.status as 状态, r.created_at as 开方时间
                        from prescription r left join patient p on p.id = r.patient_id
                        left join sys_user u on u.id = r.doctor_id
                        """;
            }
            case "stock" -> {
                dateColumn = "l.created_at";
                sql = """
                        select l.created_at as 时间, l.drug_name as 药品, l.batch_no as 批号,
                               l.change_type as 类型, l.quantity as 变动数量, l.before_qty as 变动前,
                               l.after_qty as 变动后, l.operator_name as 操作人, l.remark as 备注
                        from stock_ledger l
                        """;
            }
            case "audit" -> {
                dateColumn = "a.created_at";
                sql = """
                        select a.created_at as 时间, a.real_name as 操作人, a.username as 账号, a.role as 角色,
                               a.module as 模块, a.action as 动作, a.target_type as 对象类型,
                               a.target_id as 对象ID, a.result as 结果, a.ip as IP, a.detail as 详情
                        from audit_log a
                        """;
            }
            case "traces" -> {
                dateColumn = "t.bind_time";
                sql = """
                        select t.code as 追溯码, t.drug_name as 药品, t.batch_no as 批号, t.status as 状态,
                               t.bind_time as 入库时间, t.dispense_time as 发药时间, t.patient_name as 患者,
                               t.prescription_id as 处方ID, t.operator_name as 操作人, t.upload_status as 上传状态
                        from drug_trace t
                        """;
            }
            default -> throw new BusinessException("不支持导出的数据类型：" + type);
        }

        List<Map<String, Object>> rows;
        if (from != null) {
            rows = jdbc.queryForList(sql + " where " + dateColumn + " >= ? and " + dateColumn + " < ?", from, to);
        } else {
            rows = jdbc.queryForList(sql);
        }

        auditService.record("数据导出", "导出 " + normalized, normalized, null, "SUCCESS",
                "导出 " + rows.size() + " 行" + (start == null ? "" : "，范围 " + start + " ~ " + end));
        return toCsv(rows).getBytes(StandardCharsets.UTF_8);
    }

    /** 生成带 BOM 的 CSV（Excel 打开中文不乱码）。 */
    static String toCsv(List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("\uFEFF");
        if (rows.isEmpty()) {
            builder.append("无数据").append("\r\n");
            return builder.toString();
        }
        Map<String, Object> first = rows.get(0);
        builder.append(String.join(",", first.keySet())).append("\r\n");
        for (Map<String, Object> row : rows) {
            boolean firstCell = true;
            for (Object value : row.values()) {
                if (!firstCell) {
                    builder.append(',');
                }
                builder.append(escape(value));
                firstCell = false;
            }
            builder.append("\r\n");
        }
        return builder.toString();
    }

    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    /** 供控制器记录导出人（当前登录用户）。 */
    public String currentOperator() {
        try {
            return SecurityUtils.currentUser().realName();
        } catch (Exception ex) {
            return "system";
        }
    }
}
