package com.fangjian.clinic.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 给药途径与用药频次字典（与 /api/dict/dosage 返回一致，服务端据此把代码转成中文）。
 */
public final class DosageDict {

    private DosageDict() {
    }

    public static final Map<String, String> ROUTES = new LinkedHashMap<>();
    public static final Map<String, String> FREQUENCIES = new LinkedHashMap<>();

    static {
        ROUTES.put("po", "口服");
        ROUTES.put("jian", "煎服（水煎服）");
        ROUTES.put("chong", "冲服");
        ROUTES.put("han", "含服");
        ROUTES.put("shexia", "舌下含服");
        ROUTES.put("top", "外用");
        ROUTES.put("xunxi", "熏洗");
        ROUTES.put("di_yan", "滴眼");
        ROUTES.put("di_er", "滴耳");
        ROUTES.put("di_bi", "滴鼻");
        ROUTES.put("pen_bi", "喷鼻");
        ROUTES.put("xiru", "吸入");
        ROUTES.put("guanchang", "灌肠");
        ROUTES.put("zhichang", "直肠给药");
        ROUTES.put("bisi", "鼻饲");
        ROUTES.put("cha", "代茶饮");

        FREQUENCIES.put("qd", "一日一次");
        FREQUENCIES.put("bid", "一日两次");
        FREQUENCIES.put("tid", "一日三次");
        FREQUENCIES.put("qid", "一日四次");
        FREQUENCIES.put("q6h", "每6小时一次");
        FREQUENCIES.put("q8h", "每8小时一次");
        FREQUENCIES.put("q12h", "每12小时一次");
        FREQUENCIES.put("qod", "隔日一次");
        FREQUENCIES.put("biw", "每周两次");
        FREQUENCIES.put("qw", "每周一次");
        FREQUENCIES.put("prn", "必要时");
        FREQUENCIES.put("sos", "需要时");
        FREQUENCIES.put("st", "立即");
        FREQUENCIES.put("hs", "睡前");
        FREQUENCIES.put("ac", "饭前");
        FREQUENCIES.put("pc", "饭后");
    }

    public static String routeLabel(String code) {
        return code == null ? null : ROUTES.get(code);
    }

    public static String frequencyLabel(String code) {
        return code == null ? null : FREQUENCIES.get(code);
    }

    /** 拼装规范用法：口服，一日三次。任一为空则只保留存在的部分。 */
    public static String compose(String routeCode, String frequencyCode) {
        String route = routeLabel(routeCode);
        String frequency = frequencyLabel(frequencyCode);
        if (route == null && frequency == null) {
            return null;
        }
        if (route == null) {
            return frequency;
        }
        if (frequency == null) {
            return route;
        }
        return route + "，" + frequency;
    }
}
