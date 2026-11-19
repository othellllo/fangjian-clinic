package com.yiguan.his.service;

import com.yiguan.his.common.BusinessException;
import com.yiguan.his.dto.ChargeOrderDto;
import com.yiguan.his.dto.PrescriptionDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 打印模板：后端直接生成带打印样式的 HTML，浏览器打开后自动调起打印。
 *
 * <p>为什么用 HTML 而不是 PDF/ESC-POS：零依赖、任何打印机都能打、可预览，
 * 小票机只要在浏览器打印对话框里选热敏打印机并设 100% 缩放即可。</p>
 */
@Service
public class PrintService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChargeService chargeService;
    private final PrescriptionService prescriptionService;
    private final ConfigService configService;
    private final SettingsService settingsService;

    public PrintService(ChargeService chargeService,
                        PrescriptionService prescriptionService,
                        ConfigService configService,
                        SettingsService settingsService) {
        this.chargeService = chargeService;
        this.prescriptionService = prescriptionService;
        this.configService = configService;
        this.settingsService = settingsService;
    }

    /** 收费小票（58mm / 80mm 热敏）。 */
    public String receiptHtml(Long chargeOrderId) {
        ChargeOrderDto order = chargeService.detail(chargeOrderId);
        if (order == null) {
            throw BusinessException.notFound("收费单");
        }
        Map<String, Object> settings = settingsService.printSettings();
        int width = "80".equals(String.valueOf(settings.get("receiptWidth"))) ? 80 : 58;
        Map<String, String> clinic = configService.clinicInfo();
        boolean showClinic = !Boolean.FALSE.equals(settings.get("showClinicName"));
        String header = str(settings.get("header"));
        String footer = str(settings.get("footer"));

        StringBuilder rows = new StringBuilder();
        if (order.items() != null) {
            for (ChargeOrderDto.Item item : order.items()) {
                rows.append("<tr><td colspan=4 class=\"item\">").append(esc(item.itemName())).append("</td></tr>")
                        .append("<tr class=\"sub\"><td>").append(money(item.unitPrice()))
                        .append(" × ").append(num(item.quantity())).append("</td>")
                        .append("<td colspan=3 class=\"right\">").append(money(item.amount())).append("</td></tr>");
            }
        }

        String refundTag = "REFUNDED".equals(String.valueOf(order.status()))
                ? "<div class=\"refund\">** 已退费 **</div>" : "";

        return """
                <!DOCTYPE html>
                <html lang="zh-CN"><head><meta charset="utf-8"><title>收费小票</title><style>
                @page { size: %dmm auto; margin: 0; }
                * { box-sizing: border-box; }
                body { width: %dmm; margin: 0 auto; padding: 2mm 3mm; font-family: "SimSun","Microsoft YaHei",monospace;
                       font-size: 12px; line-height: 1.45; color: #000; }
                h1 { font-size: 15px; text-align: center; margin: 0 0 2px; }
                .center { text-align: center; }
                .muted { color: #333; }
                .line { border-top: 1px dashed #000; margin: 4px 0; }
                table { width: 100%%; border-collapse: collapse; }
                td { padding: 0; font-size: 12px; }
                .item { padding-top: 2px; }
                .sub { color: #333; }
                .right { text-align: right; }
                .total { font-size: 14px; font-weight: bold; margin-top: 3px; }
                .refund { text-align: center; font-weight: bold; margin: 4px 0; }
                .foot { margin-top: 4px; font-size: 11px; text-align: center; color: #333; }
                </style></head><body>
                %s
                %s
                <div class="center">%s</div>
                <div class="line"></div>
                <table>
                  <tr><td>单号</td><td colspan=3>%s</td></tr>
                  <tr><td>时间</td><td colspan=3>%s</td></tr>
                  <tr><td>患者</td><td colspan=3>%s</td></tr>
                </table>
                <div class="line"></div>
                <table>%s</table>
                <div class="line"></div>
                <div class="total right">合计：%s 元</div>
                <table>
                  <tr><td>支付方式</td><td class="right" colspan=3>%s</td></tr>
                  <tr><td>实收</td><td class="right" colspan=3>%s 元</td></tr>
                </table>
                %s
                <div class="line"></div>
                <div class="foot">%s</div>
                <div class="foot">打印时间：%s</div>
                <script>window.onload=function(){setTimeout(function(){window.print()},250)}</script>
                </body></html>
                """.formatted(
                width, width,
                showClinic ? "<h1>" + esc(clinic.get("name")) + "</h1>" : "",
                header.isBlank() ? "" : "<div class=\"center muted\">" + esc(header) + "</div>",
                esc(clinic.getOrDefault("phone", "")),
                esc(order.orderNo()),
                order.createdAt() == null ? "" : DATE_TIME.format(order.createdAt()),
                esc(order.patientName()),
                rows.toString(),
                money(order.payableAmount()),
                payMethodText(order.payMethod() == null ? null : order.payMethod().name()),
                money(order.paidAmount()),
                refundTag,
                footer.isBlank() ? "谢谢惠顾，祝您健康" : esc(footer),
                DATE_TIME.format(LocalDateTime.now()));
    }

    /** 处方笺（A5）。 */
    public String prescriptionHtml(Long prescriptionId) {
        PrescriptionDto prescription = prescriptionService.detail(prescriptionId);
        Map<String, String> clinic = configService.clinicInfo();
        StringBuilder rows = new StringBuilder();
        int index = 1;
        if (prescription.items() != null) {
            for (PrescriptionDto.Item item : prescription.items()) {
                rows.append("<tr>")
                        .append("<td class=\"c\">").append(index++).append("</td>")
                        .append("<td>").append(esc(item.drugName())).append("</td>")
                        .append("<td>").append(esc(item.spec() == null ? "" : item.spec())).append("</td>")
                        .append("<td class=\"c\">").append(item.dose() == null ? "" : num(item.dose())).append("</td>")
                        .append("<td class=\"c\">").append(num(item.quantity())).append(esc(item.unit() == null ? "" : item.unit())).append("</td>")
                        .append("<td>").append(esc(item.usageText() == null ? "" : item.usageText())).append("</td>")
                        .append("</tr>");
            }
        }
        return """
                <!DOCTYPE html>
                <html lang="zh-CN"><head><meta charset="utf-8"><title>中药处方笺</title><style>
                @page { size: A5; margin: 10mm; }
                body { font-family: "SimSun","Microsoft YaHei",serif; font-size: 13px; color: #000; }
                h1 { text-align: center; font-size: 20px; letter-spacing: 6px; margin: 0 0 4px; }
                .sub { text-align: center; font-size: 12px; margin-bottom: 8px; }
                .meta { display: flex; justify-content: space-between; font-size: 13px; margin: 6px 0; }
                table { width: 100%%; border-collapse: collapse; margin-top: 6px; }
                th, td { border-bottom: 1px solid #999; padding: 4px 3px; font-size: 12px; }
                th { border-top: 1px solid #999; text-align: left; }
                td.c, th.c { text-align: center; }
                .footer { margin-top: 10px; display: flex; justify-content: space-between; font-size: 13px; }
                .note { margin-top: 6px; font-size: 12px; color: #333; }
                </style></head><body>
                <h1>%s</h1>
                <div class="sub">处 方 笺</div>
                <div class="meta">
                  <span>姓名：%s</span>
                  <span>处方号：%s</span>
                  <span>日期：%s</span>
                </div>
                <div class="meta">
                  <span>医师：%s</span>
                  <span>剂数：%s 剂</span>
                  <span>煎法：%s</span>
                </div>
                <table>
                  <thead><tr><th class="c">序号</th><th>药品</th><th>规格</th><th class="c">单次剂量</th><th class="c">数量</th><th>用法</th></tr></thead>
                  <tbody>%s</tbody>
                </table>
                <div class="note">备注：%s</div>
                <div class="footer">
                  <span>合计：%s 元</span>
                  <span>医师签名：____________</span>
                  <span>审核药师：____________</span>
                </div>
                <div class="note">打印时间：%s</div>
                <script>window.onload=function(){setTimeout(function(){window.print()},250)}</script>
                </body></html>
                """.formatted(
                esc(clinic.get("name")),
                esc(prescription.patientName()),
                esc(prescription.prescriptionNo()),
                prescription.createdAt() == null ? "" : prescription.createdAt().toLocalDate().toString(),
                esc(prescription.doctorName()),
                prescription.doses(),
                esc(prescription.decoctionMethod() == null ? "—" : prescription.decoctionMethod()),
                rows.toString(),
                esc(prescription.remark() == null ? "" : prescription.remark()),
                money(prescription.totalAmount()),
                DATE_TIME.format(LocalDateTime.now()));
    }

    private static String payMethodText(String method) {
        if (method == null) {
            return "—";
        }
        return switch (method) {
            case "CASH" -> "现金";
            case "WECHAT" -> "微信";
            case "ALIPAY" -> "支付宝";
            case "BANK_CARD" -> "银行卡";
            case "MEMBER_BALANCE" -> "会员余额";
            case "INSURANCE" -> "医保";
            default -> method;
        };
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String money(java.math.BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String num(java.math.BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    /** 防止药名/备注里的特殊字符破坏 HTML。 */
    static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
