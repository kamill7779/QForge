package io.github.kamill7779.qforge.ocr;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.kamill7779.qforge.ocr.controller.OcrTaskController;
import io.github.kamill7779.qforge.ocr.dto.OcrTaskAcceptedResponse;
import io.github.kamill7779.qforge.ocr.service.OcrTaskApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OcrTaskController.class)
class OcrTaskApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OcrTaskApplicationService ocrTaskApplicationService;

    @Test
    void shouldCreateOcrTask() throws Exception {
        when(ocrTaskApplicationService.createTask(any()))
                .thenReturn(new OcrTaskAcceptedResponse("task-uuid-1", "PENDING"));

        mockMvc.perform(post("/internal/ocr/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bizType": "QUESTION_STEM",
                                  "bizId": "q-1",
                                  "imageBase64": "iVBORw0KGgoAAAANSUhEUgAAAAUA",
                                  "requestUser": "admin"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskUuid").value("task-uuid-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}

