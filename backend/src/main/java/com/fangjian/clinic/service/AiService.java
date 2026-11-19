package com.fangjian.clinic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fangjian.clinic.common.BusinessException;
import com.fangjian.clinic.dto.QcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 助写：默认离线模板，可接 OpenAI 兼容接口（Ollama / vLLM / DeepSeek 等）。
 *
 * <p><b>数据安全前提</b>：默认 LOCAL 模式不联网；切到 OPENAI_COMPATIBLE 会把病历上下文
 * 发送到配置的模型服务，必须由管理员显式开启并在界面提示。</p>
 *
 * <p>AI 只产出草稿，医生确认后才写入病历，落库时以医生为责任人。</p>
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private static final String KEY_ENABLED = "ai.enabled";
    private static final String KEY_PROVIDER = "ai.provider";
    private static final String KEY_BASE_URL = "ai.base_url";
    private static final String KEY_MODEL = "ai.model";
    private static final String KEY_API_KEY = "ai.api_key";

    private final ConfigService configService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();

    public AiService(ConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> settings() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", Boolean.parseBoolean(configService.get(KEY_ENABLED, "false")));
        data.put("provider", configService.get(KEY_PROVIDER, "LOCAL"));
        data.put("baseUrl", configService.get(KEY_BASE_URL, ""));
        data.put("model", configService.get(KEY_MODEL, ""));
        data.put("hasApiKey", !configService.get(KEY_API_KEY, "").isBlank());
        return data;
    }

    public Map<String, Object> updateSettings(Map<String, Object> body) {
        if (body.containsKey("enabled")) {
            configService.set(KEY_ENABLED, String.valueOf(Boolean.TRUE.equals(body.get("enabled"))), "AI 助写开关");
        }
        if (body.containsKey("provider")) {
            String provider = String.valueOf(body.get("provider"));
            if (!provider.equals("LOCAL") && !provider.equals("OPENAI_COMPATIBLE")) {
                throw new BusinessException("不支持的 AI 提供方：" + provider);
            }
            configService.set(KEY_PROVIDER, provider, "AI 提供方");
        }
        if (body.containsKey("baseUrl")) {
            configService.set(KEY_BASE_URL, str(body.get("baseUrl")), "AI 接口地址");
        }
        if (body.containsKey("model")) {
            configService.set(KEY_MODEL, str(body.get("model")), "AI 模型");
        }
        if (body.containsKey("apiKey") && !str(body.get("apiKey")).isBlank()) {
            configService.set(KEY_API_KEY, str(body.get("apiKey")), "AI 密钥");
        }
        return settings();
    }

    /**
     * 生成病历草稿。
     *
     * @param field 字段名：presentIllness / treatmentPrinciple / advice / physicalExam
     */
    public QcResult.AiResult assist(String field, String recordType, Map<String, Object> context) {
        boolean enabled = Boolean.parseBoolean(configService.get(KEY_ENABLED, "false"));
        String provider = configService.get(KEY_PROVIDER, "LOCAL");
        String local = localDraft(field, context);
        if (!enabled || "LOCAL".equals(provider)) {
            String notice = enabled
                    ? "当前为本地模板模式（不联网）：根据已填写的结构化信息生成草稿，请医生核对后修改"
                    : "AI 助写未开启，已用本地模板生成草稿；如需模型生成请在「系统设置 → AI 助写」开启";
            return new QcResult.AiResult(false, "LOCAL", local, notice);
        }
        try {
            String text = callOpenAiCompatible(field, recordType, context);
            if (text == null || text.isBlank()) {
                return new QcResult.AiResult(true, "LOCAL", local, "模型返回为空，已回退本地模板");
            }
            return new QcResult.AiResult(true, "LLM", text,
                    "内容由模型生成，需医生核对修改后保存；病历责任人为签名医师");
        } catch (Exception ex) {
            log.warn("AI 助写调用失败：{}", ex.getMessage());
            return new QcResult.AiResult(true, "LOCAL", local, "模型调用失败（" + ex.getMessage() + "），已回退本地模板");
        }
    }

    /* ---------------- 本地模板 ---------------- */

    private String localDraft(String field, Map<String, Object> context) {
        Map<String, Object> ctx = context == null ? Map.of() : context;
        String complaint = str(ctx.get("chiefComplaint"));
        String duration = str(ctx.get("duration"));
        String tongue = str(ctx.get("tongue"));
        String pulse = str(ctx.get("pulse"));
        String syndrome = str(ctx.get("syndrome"));
        String four = "";
        if (!tongue.isBlank() || !pulse.isBlank()) {
            four = "查体：" + (tongue.isBlank() ? "" : "舌" + tongue + "，") + (pulse.isBlank() ? "" : "脉" + pulse + "。");
        }
        return switch (field == null ? "" : field) {
            case "presentIllness" -> "患者" + (duration.isBlank() ? "近期" : duration + "前")
                    + "无明显诱因出现" + (complaint.isBlank() ? "上述不适" : complaint)
                    + "，症状呈持续性，未予系统诊治。发病以来神志清，精神可，饮食睡眠一般，二便调。" + four;
            case "treatmentPrinciple" -> syndrome.isBlank()
                    ? "（请先填写证候诊断，系统将据此生成治则草稿）"
                    : "治以" + syndromeToPrinciple(syndrome) + "。";
            case "advice" -> "1. 嘱清淡饮食，忌生冷油腻；2. 规律作息，避免劳累；3. 中药水煎服，日一剂，分早晚温服；4. 不适随诊。";
            case "physicalExam" -> "T 36.5℃，P 78 次/分，R 18 次/分，BP 120/80 mmHg。神志清，精神可，"
                    + "双肺呼吸音清，未闻及干湿性啰音，心律齐，腹软，无压痛及反跳痛，双下肢无水肿。";
            default -> "";
        };
    }

    private String syndromeToPrinciple(String syndrome) {
        if (syndrome.contains("气虚")) {
            return "健脾益气";
        }
        if (syndrome.contains("阳虚")) {
            return "温阳散寒";
        }
        if (syndrome.contains("阴虚")) {
            return "滋阴清热";
        }
        if (syndrome.contains("血瘀")) {
            return "活血化瘀";
        }
        if (syndrome.contains("痰")) {
            return "化痰祛湿";
        }
        if (syndrome.contains("肝郁")) {
            return "疏肝解郁";
        }
        if (syndrome.contains("湿热")) {
            return "清热利湿";
        }
        return "辨证论治（请据证型补充）";
    }

    /* ---------------- OpenAI 兼容调用 ---------------- */

    private String callOpenAiCompatible(String field, String recordType, Map<String, Object> context) throws Exception {
        String baseUrl = configService.get(KEY_BASE_URL, "");
        String model = configService.get(KEY_MODEL, "");
        String apiKey = configService.get(KEY_API_KEY, "");
        if (baseUrl.isBlank() || model.isBlank()) {
            throw new BusinessException("未配置模型地址或模型名");
        }
        String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

        String system = "你是中医馆电子病历助手。只输出病历片段正文，不要解释、不要加引号。"
                + "中医病历用规范术语，不得编造未提供的信息；信息不足时用「（待补充）」。";
        String user = "病历类型：" + (recordType == null ? "TCM" : recordType)
                + "；需要生成的字段：" + field
                + "；已知信息：" + objectMapper.writeValueAsString(context == null ? Map.of() : context);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.3);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (!apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new BusinessException("模型接口返回 " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode content = root.at("/choices/0/message/content");
        return content.isMissingNode() ? null : content.asText();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
