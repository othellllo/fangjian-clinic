package com.yiguan.his;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口级集成测试：验证鉴权与核心建档流程在真实 Spring 上下文中可用。
 * 使用 H2 内存库 + Flyway 迁移，测试数据随上下文销毁。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("登录成功返回 token，并可携带 token 建档")
    void loginAndCreatePatient() throws Exception {
        String loginJson = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode login = objectMapper.readTree(loginJson);
        assertEquals(0, login.get("code").asInt(), "登录应成功");
        String token = login.at("/data/token").asText();
        assertFalse(token.isBlank(), "登录应返回 token");
        assertEquals("ADMIN", login.at("/data/user/role").asText());

        String patientJson = mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试患者\",\"gender\":\"FEMALE\",\"birthDate\":\"1992-03-08\",\"phone\":\"13800002222\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode patient = objectMapper.readTree(patientJson);
        assertEquals(0, patient.get("code").asInt(), "建档应成功");
        assertTrue(patient.at("/data/patientNo").asText().startsWith("P"), "应生成病历号");
        assertEquals("测试患者", patient.at("/data/name").asText());
    }

    @Test
    @DisplayName("未登录访问业务接口返回 401")
    void unauthorizedRequestRejected() throws Exception {
        mockMvc.perform(get("/api/patients")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("密码错误返回业务错误码 401")
    void wrongPasswordRejected() throws Exception {
        String json = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(401, objectMapper.readTree(json).get("code").asInt());
    }
}
