package com.salt.field.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salt.field.domain.Pond;
import com.salt.field.repository.DispatchStatusLogRepository;
import com.salt.field.repository.DispatchTaskRepository;
import com.salt.field.repository.PondRepository;

/**
 * 派工接口 HTTP 语义：合法推进 200、非法/倒退/完成后修改 400、并发/旧页面 409（带快照）、
 * 幂等重复提交 200 且消息标明无变化；列表对异常行/别名行口径一致。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DispatchControllerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PondRepository pondRepository;

    @Autowired
    private DispatchTaskRepository taskRepository;

    @Autowired
    private DispatchStatusLogRepository logRepository;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        logRepository.deleteAll();
        if (pondRepository.count() == 0) {
            Pond pond = new Pond();
            pond.setName("一号结晶池");
            pond.setPondNo("C-HTTP-1");
            pondRepository.save(pond);
        }
    }

    private long createTodo() throws Exception {
        long pondId = pondRepository.findAll().get(0).getId();
        String body = json.writeValueAsString(java.util.Map.of(
                "taskType", "FIX",
                "targetType", "POND",
                "targetId", pondId,
                "crew", "crew1",
                "operator", "dispatcherA",
                "remark", "http test"));
        MvcResult r = mvc.perform(post("/api/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskStatus").value("TODO"))
                .andExpect(jsonPath("$.data.nextStatus").value("DOING"))
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    @Test
    void http_advance_skip_and_backward_and_unknown_return_400_with_actionable_reason() throws Exception {
        long id = createTodo();

        // 跳级 TODO->DONE
        mvc.perform(put("/api/dispatch/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskStatus\":\"DONE\",\"expectedStatus\":\"TODO\",\"operator\":\"dispatcherA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("跳级")));

        // 未知目标值
        mvc.perform(put("/api/dispatch/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskStatus\":\"WAT\",\"expectedStatus\":\"TODO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("目标状态取值有误")));

        // 推进到 DOING 后再退回 TODO
        mvc.perform(put("/api/dispatch/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskStatus\":\"DOING\",\"expectedStatus\":\"TODO\",\"operator\":\"dispatcherA\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/dispatch/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskStatus\":\"TODO\",\"expectedStatus\":\"DOING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("退回")));

        // DONE 后任何修改都 400
        mvc.perform(put("/api/dispatch/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskStatus\":\"DONE\",\"expectedStatus\":\"DOING\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/dispatch/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskStatus\":\"DOING\",\"expectedStatus\":\"DONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已完成")));
    }

    @Test
    void http_idempotent_duplicate_returns_200_without_change_message() throws Exception {
        long id = createTodo();
        mvc.perform(put("/api/dispatch/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskStatus\":\"DOING\",\"expectedStatus\":\"TODO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已推进")));

        mvc.perform(put("/api/dispatch/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskStatus\":\"DOING\",\"expectedStatus\":\"DOING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("幂等")));
    }

    @Test
    void http_legacy_client_without_expected_status_still_enforced_by_state_machine() throws Exception {
        long id = createTodo();
        // 不带 expectedStatus：跳级仍被拒
        mvc.perform(put("/api/dispatch/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskStatus\":\"DONE\"}"))
                .andExpect(status().isBadRequest());
        // 合法前进可以
        mvc.perform(put("/api/dispatch/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskStatus\":\"DOING\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void http_stale_page_submission_returns_409_with_latest_snapshot() throws Exception {
        long id = createTodo();
        // 甲推进到 DOING
        mvc.perform(put("/api/dispatch/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskStatus\":\"DOING\",\"expectedStatus\":\"TODO\",\"operator\":\"dispatcherA\"}"))
                .andExpect(status().isOk());
        // 乙持旧页面（快照 TODO）提交同一目标 DOING：幂等 200（无变化、无重复留痕）
        mvc.perform(put("/api/dispatch/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskStatus\":\"DOING\",\"expectedStatus\":\"TODO\",\"operator\":\"dispatcherB\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("幂等")));
        // 乙持旧页面（快照 TODO）提交分叉目标 DONE：409，data 为最新快照（DOING + 生效时间）
        MvcResult res = mvc.perform(put("/api/dispatch/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskStatus\":\"DONE\",\"expectedStatus\":\"TODO\",\"operator\":\"dispatcherB\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已被另一名调度员改动")))
                .andExpect(jsonPath("$.data.taskStatus").value("DOING"))
                .andExpect(jsonPath("$.data.statusChangedAt").isNotEmpty())
                .andReturn();
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.path("data").path("lastOperator").asText()).isEqualTo("dispatcherA");

        // 甲再完成 DONE，乙持 DOING 旧页面想退回 TODO -> 409，DONE 不被覆盖
        mvc.perform(put("/api/dispatch/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskStatus\":\"DONE\",\"expectedStatus\":\"DOING\",\"operator\":\"dispatcherA\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/dispatch/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskStatus\":\"TODO\",\"expectedStatus\":\"DOING\",\"operator\":\"dispatcherB\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.taskStatus").value("DONE"));
    }

    @Test
    void http_list_and_detail_expose_abnormal_and_alias_flags_consistently() throws Exception {
        long id = createTodo();
        mvc.perform(get("/api/dispatch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].taskStatus").exists());
        mvc.perform(get("/api/dispatch/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskStatus").value("TODO"))
                .andExpect(jsonPath("$.data.abnormal").value(false))
                .andExpect(jsonPath("$.data.nextStatus").value("DOING"))
                .andExpect(jsonPath("$.data.history[0].changeType").value("CREATE"));
    }
}
