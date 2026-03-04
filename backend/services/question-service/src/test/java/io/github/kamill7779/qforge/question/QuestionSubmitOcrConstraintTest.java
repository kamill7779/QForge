package io.github.kamill7779.qforge.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.question.client.OcrServiceClient;
import io.github.kamill7779.qforge.question.config.QForgeBusinessProperties;
import io.github.kamill7779.qforge.question.dto.OcrTaskAcceptedResponse;
import io.github.kamill7779.qforge.question.dto.OcrTaskSubmitRequest;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.exception.BusinessValidationException;
import io.github.kamill7779.qforge.question.redis.TaskStateRedisService;
import io.github.kamill7779.qforge.question.repository.AnswerRepository;
import io.github.kamill7779.qforge.question.repository.QuestionAiTaskRepository;
import io.github.kamill7779.qforge.question.repository.QuestionAssetRepository;
import io.github.kamill7779.qforge.question.repository.QuestionOcrTaskRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
import io.github.kamill7779.qforge.question.repository.QuestionTagRelRepository;
import io.github.kamill7779.qforge.question.repository.TagCategoryRepository;
import io.github.kamill7779.qforge.question.repository.TagRepository;
import io.github.kamill7779.qforge.question.service.QuestionCommandServiceImpl;
import io.github.kamill7779.qforge.question.validation.StemXmlValidator;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class QuestionSubmitOcrConstraintTest {

    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private QuestionAssetRepository questionAssetRepository;
    @Mock
    private QuestionOcrTaskRepository questionOcrTaskRepository;
    @Mock
    private QuestionAiTaskRepository questionAiTaskRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private TagCategoryRepository tagCategoryRepository;
    @Mock
    private QuestionTagRelRepository questionTagRelRepository;
    @Mock
    private OcrServiceClient ocrServiceClient;
    @Mock
    private StemXmlValidator stemXmlValidator;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private TaskStateRedisService taskStateRedisService;
    @Mock
    private QForgeBusinessProperties businessProperties;
    @Mock
    private StringRedisTemplate redis;

    @InjectMocks
    private QuestionCommandServiceImpl questionCommandService;

    @Test
    void shouldRejectWhenAnswerOcrTaskIsInProgressInDb() {
        when(questionRepository.findByQuestionUuidAndOwnerUser("q-1", "admin"))
                .thenReturn(Optional.of(draftQuestion("q-1", "admin")));
        when(questionOcrTaskRepository.existsByQuestionUuidAndBizTypeAndStatusIn(
                eq("q-1"), eq("ANSWER_CONTENT"), anyCollection()))
                .thenReturn(true);

        BusinessValidationException ex = assertThrows(
                BusinessValidationException.class,
                () -> questionCommandService.submitOcrTask("q-1", answerOcrRequest(), "admin")
        );

        assertEquals("OCR_TASK_CONFLICT", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        verify(taskStateRedisService, never()).tryAcquireAnswerOcrGuard(any(), any());
        verify(ocrServiceClient, never()).createTask(any());
    }

    @Test
    void shouldRejectWhenAnswerOcrGuardKeyAlreadyHeld() {
        when(questionRepository.findByQuestionUuidAndOwnerUser("q-1", "admin"))
                .thenReturn(Optional.of(draftQuestion("q-1", "admin")));
        when(questionOcrTaskRepository.existsByQuestionUuidAndBizTypeAndStatusIn(
                eq("q-1"), eq("ANSWER_CONTENT"), anyCollection()))
                .thenReturn(false);
        when(taskStateRedisService.tryAcquireAnswerOcrGuard("q-1", "admin"))
                .thenReturn(false);

        BusinessValidationException ex = assertThrows(
                BusinessValidationException.class,
                () -> questionCommandService.submitOcrTask("q-1", answerOcrRequest(), "admin")
        );

        assertEquals("OCR_TASK_CONFLICT", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        verify(ocrServiceClient, never()).createTask(any());
    }

    @Test
    void shouldReleaseAnswerOcrGuardWhenRemoteTaskCreationFails() {
        when(questionRepository.findByQuestionUuidAndOwnerUser("q-1", "admin"))
                .thenReturn(Optional.of(draftQuestion("q-1", "admin")));
        when(questionOcrTaskRepository.existsByQuestionUuidAndBizTypeAndStatusIn(
                eq("q-1"), eq("ANSWER_CONTENT"), anyCollection()))
                .thenReturn(false);
        when(taskStateRedisService.tryAcquireAnswerOcrGuard("q-1", "admin"))
                .thenReturn(true);
        when(ocrServiceClient.createTask(any()))
                .thenThrow(new RuntimeException("ocr service unavailable"));

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> questionCommandService.submitOcrTask("q-1", answerOcrRequest(), "admin")
        );

        assertEquals("ocr service unavailable", ex.getMessage());
        verify(taskStateRedisService).releaseAnswerOcrGuard("q-1");
    }

    @Test
    void shouldAllowAnswerOcrSubmitWhenNoConflict() {
        when(questionRepository.findByQuestionUuidAndOwnerUser("q-1", "admin"))
                .thenReturn(Optional.of(draftQuestion("q-1", "admin")));
        when(questionOcrTaskRepository.existsByQuestionUuidAndBizTypeAndStatusIn(
                eq("q-1"), eq("ANSWER_CONTENT"), anyCollection()))
                .thenReturn(false);
        when(taskStateRedisService.tryAcquireAnswerOcrGuard("q-1", "admin"))
                .thenReturn(true);
        when(ocrServiceClient.createTask(any()))
                .thenReturn(new OcrTaskAcceptedResponse("task-1", "PENDING"));

        OcrTaskAcceptedResponse response = questionCommandService.submitOcrTask("q-1", answerOcrRequest(), "admin");

        assertEquals("task-1", response.taskUuid());
        assertEquals("PENDING", response.status());
        verify(taskStateRedisService, never()).releaseAnswerOcrGuard("q-1");
    }

    private OcrTaskSubmitRequest answerOcrRequest() {
        OcrTaskSubmitRequest request = new OcrTaskSubmitRequest();
        request.setBizType("ANSWER_CONTENT");
        request.setImageBase64("BASE64_IMAGE");
        return request;
    }

    private Question draftQuestion(String questionUuid, String ownerUser) {
        Question question = new Question();
        question.setId(1L);
        question.setQuestionUuid(questionUuid);
        question.setOwnerUser(ownerUser);
        question.setStatus("DRAFT");
        return question;
    }
}
