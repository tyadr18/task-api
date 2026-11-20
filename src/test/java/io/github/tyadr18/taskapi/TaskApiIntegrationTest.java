package io.github.tyadr18.taskapi;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.tyadr18.taskapi.repository.TaskRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * アプリ全体を起動し、実際のDB（既定はH2）を通してAPIを確認する。
 * CIでは SPRING_PROFILES_ACTIVE=postgres を付けて PostgreSQL でも同じテストを流す。
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void cleanUp() {
        taskRepository.deleteAll();
    }

    @Test
    @DisplayName("作成 → 取得 → 更新 → 削除 → 404 の一連の流れ")
    void crudLifecycle() throws Exception {
        long id = createTask("""
                {"title": "見積書を作る", "description": "A社向け", "priority": "HIGH", "dueDate": "2026-12-10"}
                """);

        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("見積書を作る"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.dueDate").value("2026-12-10"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(put("/api/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "見積書を送る", "status": "DONE", "priority": "MEDIUM"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("見積書を送る"))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"));

        mockMvc.perform(delete("/api/tasks/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("ステータスと優先度で絞り込める")
    void filtersByStatusAndPriority() throws Exception {
        createTask("{\"title\": \"A\", \"status\": \"TODO\", \"priority\": \"HIGH\"}");
        createTask("{\"title\": \"B\", \"status\": \"TODO\", \"priority\": \"LOW\"}");
        createTask("{\"title\": \"C\", \"status\": \"DONE\", \"priority\": \"HIGH\"}");

        mockMvc.perform(get("/api/tasks").param("status", "TODO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/tasks").param("status", "TODO").param("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("A"));
    }

    @Test
    @DisplayName("優先度のソートは名前順ではなく LOW < MEDIUM < HIGH の順になる")
    void sortsByPriorityLevel() throws Exception {
        createTask("{\"title\": \"中\", \"priority\": \"MEDIUM\"}");
        createTask("{\"title\": \"高\", \"priority\": \"HIGH\"}");
        createTask("{\"title\": \"低\", \"priority\": \"LOW\"}");

        mockMvc.perform(get("/api/tasks").param("sort", "priority,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].priority", contains("HIGH", "MEDIUM", "LOW")));
    }

    @Test
    @DisplayName("ページネーションと件数の上限")
    void paginates() throws Exception {
        for (int i = 1; i <= 3; i++) {
            createTask("{\"title\": \"タスク" + i + "\"}");
        }

        mockMvc.perform(get("/api/tasks").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("タスク3"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(true));

        mockMvc.perform(get("/api/tasks").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("許可されていない sort は 400 になり、500にならない")
    void rejectsUnknownSort() throws Exception {
        mockMvc.perform(get("/api/tasks").param("sort", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("存在しないURLも 404 をJSONで返す")
    void unknownUrlReturnsJson404() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private long createTask(String json) throws Exception {
        String body = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }
}
