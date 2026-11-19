package com.yiguan.his.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营报表：直接走聚合 SQL，避免把统计数据加载到内存。
 * SQL 只使用 H2（MySQL 模式）与 MySQL 8 都支持的标准语法。
 */
@Service
public class ReportService {

    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> daily(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();

        BigDecimal revenue = decimal("select coalesce(sum(amount), 0) from payment where created_at >= ? and created_at < ?", from, to);
        long registerCount = count("select count(*) from visit where register_time >= ? and register_time < ?", from, to);
        long visitCount = count("select count(*) from visit where register_time >= ? and register_time < ? and status = 'FINISHED'", from, to);
        long prescriptionCount = count("select count(*) from prescription where created_at >= ? and created_at < ?", from, to);
        BigDecimal refund = decimal("select coalesce(sum(payable_amount), 0) from charge_order where status = 'REFUNDED' and updated_at >= ? and updated_at < ?", from, to);

        List<Map<String, Object>> byMethod = new ArrayList<>();
        jdbc.query("select method, coalesce(sum(amount), 0) amount, count(*) cnt from payment "
                        + "where created_at >= ? and created_at < ? group by method order by amount desc",
                rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("method", rs.getString("method"));
                    row.put("amount", rs.getBigDecimal("amount"));
                    row.put("count", rs.getLong("cnt"));
                    byMethod.add(row);
                }, from, to);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", day.toString());
        data.put("revenue", revenue);
        data.put("registerCount", registerCount);
        data.put("visitCount", visitCount);
        data.put("prescriptionCount", prescriptionCount);
        data.put("refund", refund);
        data.put("byMethod", byMethod);
        data.put("byDoctor", doctorStats(from, to));
        data.put("byDrugs", drugStats(from, to));
        return data;
    }

    public List<Map<String, Object>> range(LocalDate start, LocalDate end) {
        LocalDate from = start == null ? LocalDate.now().minusDays(6) : start;
        LocalDate to = end == null ? LocalDate.now() : end;
        LocalDateTime fromTime = from.atStartOfDay();
        LocalDateTime toTime = to.plusDays(1).atStartOfDay();

        Map<String, Map<String, Object>> buckets = new LinkedHashMap<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", cursor.toString());
            row.put("revenue", BigDecimal.ZERO);
            row.put("visitCount", 0L);
            buckets.put(cursor.toString(), row);
        }
        jdbc.query("select cast(created_at as date) d, coalesce(sum(amount), 0) amount from payment "
                        + "where created_at >= ? and created_at < ? group by cast(created_at as date)",
                rs -> {
                    String key = rs.getDate("d").toLocalDate().toString();
                    Map<String, Object> row = buckets.get(key);
                    if (row != null) {
                        row.put("revenue", rs.getBigDecimal("amount"));
                    }
                }, fromTime, toTime);
        jdbc.query("select cast(register_time as date) d, count(*) cnt from visit "
                        + "where register_time >= ? and register_time < ? group by cast(register_time as date)",
                rs -> {
                    String key = rs.getDate("d").toLocalDate().toString();
                    Map<String, Object> row = buckets.get(key);
                    if (row != null) {
                        row.put("visitCount", rs.getLong("cnt"));
                    }
                }, fromTime, toTime);
        return new ArrayList<>(buckets.values());
    }

    public List<Map<String, Object>> doctorPerformance(LocalDate start, LocalDate end) {
        LocalDateTime fromTime = (start == null ? LocalDate.now().withDayOfMonth(1) : start).atStartOfDay();
        LocalDateTime toTime = (end == null ? LocalDate.now() : end).plusDays(1).atStartOfDay();
        return doctorStats(fromTime, toTime);
    }

    private List<Map<String, Object>> doctorStats(LocalDateTime from, LocalDateTime to) {
        Map<Long, Map<String, Object>> rows = new LinkedHashMap<>();
        jdbc.query("select v.doctor_id, u.real_name, count(*) cnt from visit v "
                        + "left join sys_user u on u.id = v.doctor_id "
                        + "where v.register_time >= ? and v.register_time < ? "
                        + "group by v.doctor_id, u.real_name",
                rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("doctorId", rs.getLong("doctor_id"));
                    row.put("doctorName", rs.getString("real_name"));
                    row.put("visitCount", rs.getLong("cnt"));
                    row.put("prescriptionCount", 0L);
                    row.put("revenue", BigDecimal.ZERO);
                    rows.put(rs.getLong("doctor_id"), row);
                }, from, to);

        jdbc.query("select v.doctor_id, coalesce(sum(c.paid_amount), 0) amount from charge_order c "
                        + "join visit v on v.id = c.visit_id "
                        + "where c.status = 'PAID' and c.paid_at >= ? and c.paid_at < ? "
                        + "group by v.doctor_id",
                rs -> {
                    Map<String, Object> row = rows.get(rs.getLong("doctor_id"));
                    if (row != null) {
                        row.put("revenue", rs.getBigDecimal("amount"));
                    }
                }, from, to);

        jdbc.query("select doctor_id, count(*) cnt from prescription "
                        + "where created_at >= ? and created_at < ? group by doctor_id",
                rs -> {
                    Map<String, Object> row = rows.get(rs.getLong("doctor_id"));
                    if (row != null) {
                        row.put("prescriptionCount", rs.getLong("cnt"));
                    }
                }, from, to);
        return new ArrayList<>(rows.values());
    }

    public List<Map<String, Object>> drugSales(LocalDate start, LocalDate end) {
        LocalDateTime fromTime = (start == null ? LocalDate.now().withDayOfMonth(1) : start).atStartOfDay();
        LocalDateTime toTime = (end == null ? LocalDate.now() : end).plusDays(1).atStartOfDay();
        return drugStats(fromTime, toTime);
    }

    private List<Map<String, Object>> drugStats(LocalDateTime from, LocalDateTime to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.query("select pi.drug_name, coalesce(sum(pi.quantity), 0) qty, coalesce(sum(pi.amount), 0) amount "
                        + "from prescription_item pi join prescription p on p.id = pi.prescription_id "
                        + "where p.created_at >= ? and p.created_at < ? and p.status <> 'CANCELLED' "
                        + "group by pi.drug_name order by amount desc",
                rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("drugName", rs.getString("drug_name"));
                    row.put("quantity", rs.getBigDecimal("qty"));
                    row.put("amount", rs.getBigDecimal("amount"));
                    rows.add(row);
                }, from, to);
        return rows.size() > 10 ? rows.subList(0, 10) : rows;
    }

    /**
     * 医保结算统计：按结算渠道汇总笔数、医保支付、个人自付、合计与均次费用。
     * 口径来源：insurance_settlement 表（结算留痕），不重算报销规则。
     */
    public List<Map<String, Object>> insuranceStats(LocalDate start, LocalDate end) {
        LocalDateTime fromTime = (start == null ? LocalDate.now().withDayOfMonth(1) : start).atStartOfDay();
        LocalDateTime toTime = (end == null ? LocalDate.now() : end).plusDays(1).atStartOfDay();
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.query("""
                        select provider_code,
                               count(*) cnt,
                               coalesce(sum(insurance_amount), 0) ins,
                               coalesce(sum(self_pay_amount), 0) self,
                               coalesce(sum(insurance_amount + self_pay_amount), 0) total
                        from insurance_settlement
                        where created_at >= ? and created_at < ?
                        group by provider_code
                        order by total desc
                        """,
                rs -> {
                    long count = rs.getLong("cnt");
                    BigDecimal insurance = rs.getBigDecimal("ins");
                    BigDecimal selfPay = rs.getBigDecimal("self");
                    BigDecimal total = rs.getBigDecimal("total");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("providerCode", rs.getString("provider_code"));
                    row.put("count", count);
                    row.put("insuranceAmount", insurance);
                    row.put("selfPayAmount", selfPay);
                    row.put("total", total);
                    row.put("avgAmount", count == 0 ? BigDecimal.ZERO
                            : total.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP));
                    rows.add(row);
                }, fromTime, toTime);
        return rows;
    }

    /** 当日医保结算汇总。 */
    public Map<String, Object> insuranceDaily(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", day.toString());
        data.put("count", count("""
                select count(*) from insurance_settlement where created_at >= ? and created_at < ?
                """, from, to));
        data.put("insuranceAmount", decimal("""
                select coalesce(sum(insurance_amount), 0) from insurance_settlement
                where created_at >= ? and created_at < ?
                """, from, to));
        data.put("selfPayAmount", decimal("""
                select coalesce(sum(self_pay_amount), 0) from insurance_settlement
                where created_at >= ? and created_at < ?
                """, from, to));
        data.put("byProvider", insuranceStats(day, day));
        List<Map<String, Object>> statusRows = new ArrayList<>();
        jdbc.query("""
                        select status, count(*) cnt from insurance_settlement
                        where created_at >= ? and created_at < ?
                        group by status
                        """,
                rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("status", rs.getString("status"));
                    row.put("count", rs.getLong("cnt"));
                    statusRows.add(row);
                }, from, to);
        data.put("byStatus", statusRows);
        return data;
    }

    private BigDecimal decimal(String sql, Object... args) {
        List<BigDecimal> values = jdbc.query(sql, (rs, rowNum) -> rs.getBigDecimal(1), args);
        if (values.isEmpty() || values.get(0) == null) {
            return BigDecimal.ZERO;
        }
        return values.get(0);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    /** 保留给后续自定义报表使用。 */
    public static Date toSqlDate(LocalDate date) {
        return Date.valueOf(date);
    }
}
