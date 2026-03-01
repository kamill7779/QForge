package io.github.kamill7779.qforge.question;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.kamill7779.qforge.question.config.GlobalExceptionHandler;
import io.github.kamill7779.qforge.question.controller.QuestionController;
import io.github.kamill7779.qforge.question.dto.AnswerOverviewResponse;
import io.github.kamill7779.qforge.question.dto.CreateQuestionRequest;
import io.github.kamill7779.qforge.question.dto.QuestionMainTagResponse;
import io.github.kamill7779.qforge.question.dto.QuestionOverviewResponse;
import io.github.kamill7779.qforge.question.dto.QuestionStatusResponse;
import io.github.kamill7779.qforge.question.exception.BusinessValidationException;
import io.github.kamill7779.qforge.question.service.QuestionCommandService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuestionController.class)
@Import(GlobalExceptionHandler.class)
class QuestionApiRestStandardTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuestionCommandService questionCommandService;

    @Test
    void shouldCreateDraftQuestion() throws Exception {
        when(questionCommandService.createDraft(any(CreateQuestionRequest.class), eq("admin")))
                .thenReturn(new QuestionStatusResponse("q-uuid-1", "DRAFT"));

        mockMvc.perform(post("/api/questions")
                        .header("X-Auth-User", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.questionUuid").value("q-uuid-1"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void shouldListAllUserQuestions() throws Exception {
        when(questionCommandService.listUserQuestions("admin")).thenReturn(List.of(
                new QuestionOverviewResponse(
                        "q-uuid-1",
                        "DRAFT",
                        "",
                        List.of(
                                new QuestionMainTagResponse("MAIN_GRADE", "Grade", "UNCATEGORIZED", "Uncategorized"),
                                new QuestionMainTagResponse("MAIN_KNOWLEDGE", "Knowledge", "UNCATEGORIZED", "Uncategorized")
                        ),
                        List.of(),
                        0,
                        List.of(),
                        LocalDateTime.of(2026, 3, 1, 0, 30, 0)
                ),
                new QuestionOverviewResponse(
                        "q-uuid-2",
                        "READY",
                        "question stem",
                        List.of(
                                new QuestionMainTagResponse("MAIN_GRADE", "Grade", "UNCATEGORIZED", "Uncategorized"),
                                new QuestionMainTagResponse("MAIN_KNOWLEDGE", "Knowledge", "UNCATEGORIZED", "Uncategorized")
                        ),
                        List.of("function"),
                        2,
                        List.of(new AnswerOverviewResponse("a-1", "LATEX_TEXT", "$x=1$", 1, false)),
                        LocalDateTime.of(2026, 3, 1, 0, 29, 0)
                )
        ));

        mockMvc.perform(get("/api/questions")
                        .header("X-Auth-User", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].questionUuid").value("q-uuid-1"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[1].questionUuid").value("q-uuid-2"))
                .andExpect(jsonPath("$[1].status").value("READY"))
                .andExpect(jsonPath("$[1].mainTags[0].categoryCode").value("MAIN_GRADE"))
                .andExpect(jsonPath("$[1].secondaryTags[0]").value("function"))
                .andExpect(jsonPath("$[1].answers[0].latexText").value("$x=1$"));
    }

    @Test
    void shouldReturn422WhenCompleteConditionsNotMet() throws Exception {
        doThrow(new BusinessValidationException(
                "QUESTION_COMPLETE_VALIDATION_FAILED",
                "stem_text is required and at least one answer is required",
                Map.of("missingFields", new String[]{"stemText", "answers"})
        )).when(questionCommandService).completeQuestion("q-uuid-1", "admin");

        mockMvc.perform(post("/api/questions/q-uuid-1/complete")
                        .header("X-Auth-User", "admin")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUESTION_COMPLETE_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.details").exists());
    }

    @Test
    void shouldDeleteDraftQuestion() throws Exception {
        mockMvc.perform(delete("/api/questions/q-uuid-2")
                        .header("X-Auth-User", "admin"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturn422WhenDeleteNotAllowed() throws Exception {
        doThrow(new BusinessValidationException(
                "QUESTION_DELETE_NOT_ALLOWED",
                "Only draft question without answers can be deleted",
                Map.of("questionUuid", "q-uuid-2")
        )).when(questionCommandService).deleteDraftQuestion("q-uuid-2", "admin");

        mockMvc.perform(delete("/api/questions/q-uuid-2")
                        .header("X-Auth-User", "admin"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUESTION_DELETE_NOT_ALLOWED"));
    }
}
