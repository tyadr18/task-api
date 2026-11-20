package io.github.tyadr18.taskapi.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tyadr18.taskapi.domain.TaskPriority;
import io.github.tyadr18.taskapi.domain.TaskStatus;
import io.github.tyadr18.taskapi.dto.PageResponse;
import io.github.tyadr18.taskapi.dto.TaskCreateRequest;
import io.github.tyadr18.taskapi.dto.TaskResponse;
import io.github.tyadr18.taskapi.exception.InvalidSortException;
import io.github.tyadr18.taskapi.exception.TaskNotFoundException;
import io.github.tyadr18.taskapi.service.TaskService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web層だけを起動し、HTTPの入出力（ステータス・JSON・エラー形式）を確認する。 */
@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @Test
    @DisplayName("POST: 201 と Location ヘッダーを返す")
    void createReturns201() throws Exception {
        when(taskService.create(any(TaskCreateRequest.class))).thenReturn(sampleResponse(1L));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "資料を作る", "priority": "HIGH", "dueDate": "2026-12-31"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/tasks/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    @DisplayName("POST: タイトルが空なら 400 と項目別のエラー")
    void createRejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/tasks"))
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").value("タイトルは必須です"));
        verifyNoInteractions(taskService);
    }

    @Test
    @DisplayName("POST: タイトルが101文字なら 400")
    void createRejectsTooLongTitle() throws Exception {
        String title = "あ".repeat(101);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + title + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").value("タイトルは100文字以内で入力してください"));
    }

    @Test
    @DisplayName("POST: 存在しないステータスなら 400")
    void createRejectsUnknownStatus() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "x", "status": "WIP"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isEmpty());
        verifyNoInteractions(taskService);
    }

    @Test
    @DisplayName("POST: JSON以外の Content-Type なら 415")
    void createRejectsNonJson() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("title=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    @DisplayName("GET /{id}: 存在しなければ 404 をJSONで返す")
    void getReturns404() throws Exception {
        when(taskService.findById(99L)).thenThrow(new TaskNotFoundException(99L));

        mockMvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("ID 99 のタスクが見つかりません"))
                .andExpect(jsonPath("$.path").value("/api/tasks/99"));
    }

    @Test
    @DisplayName("GET /{id}: IDが数値でなければ 400")
    void getRejectsNonNumericId() throws Exception {
        mockMvc.perform(get("/api/tasks/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("パラメータ 'id' の値 'abc' が不正です"));
    }

    @Test
    @DisplayName("GET: 絞り込み条件とページ指定をサービスへ渡す")
    void listPassesFiltersAndPageable() throws Exception {
        when(taskService.search(eq(TaskStatus.DOING), eq(TaskPriority.HIGH), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(sampleResponse(3L)), 1, 5, 6, 2, false, true));

        mockMvc.perform(get("/api/tasks")
                        .param("status", "DOING")
                        .param("priority", "HIGH")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "dueDate,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(6));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(taskService).search(eq(TaskStatus.DOING), eq(TaskPriority.HIGH), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().getOrderFor("dueDate")).isNotNull()
                .extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("GET: status に存在しない値を渡すと 400 で、指定できる値を案内する")
    void listRejectsUnknownStatusParam() throws Exception {
        mockMvc.perform(get("/api/tasks").param("status", "WIP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "パラメータ 'status' の値 'WIP' が不正です（指定できる値: [TODO, DOING, DONE]）"));
    }

    @Test
    @DisplayName("GET: 許可されていない sort は 400")
    void listRejectsInvalidSort() throws Exception {
        when(taskService.search(any(), any(), any(Pageable.class)))
                .thenThrow(new InvalidSortException("secret", List.of("id")));

        mockMvc.perform(get("/api/tasks").param("sort", "secret"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("PUT: status を省略すると 400")
    void updateRequiresStatus() throws Exception {
        mockMvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "x", "priority": "LOW"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value("ステータスは必須です"));
        verifyNoInteractions(taskService);
    }

    @Test
    @DisplayName("DELETE: 204 を返す")
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/tasks/1"))
                .andExpect(status().isNoContent());
        verify(taskService).delete(1L);
    }

    @Test
    @DisplayName("未対応のメソッドは 405 をJSONで返す")
    void unsupportedMethodReturns405() throws Exception {
        mockMvc.perform(patch("/api/tasks/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.status").value(405));
    }

    private static TaskResponse sampleResponse(long id) {
        Instant now = Instant.parse("2026-11-20T00:00:00Z");
        return new TaskResponse(id, "資料を作る", null, TaskStatus.TODO, TaskPriority.HIGH,
                LocalDate.of(2026, 12, 31), now, now);
    }
}
