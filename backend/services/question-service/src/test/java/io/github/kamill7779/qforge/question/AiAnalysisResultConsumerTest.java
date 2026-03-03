package io.github.kamill7779.qforge.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.common.contract.AiAnalysisResultEvent;
import io.github.kamill7779.qforge.question.entity.QuestionAiTask;
import io.github.kamill7779.qforge.question.mq.AiAnalysisResultConsumer;
import io.github.kamill7779.qforge.question.repository.QuestionAiTaskRepository;
import io.github.kamill7779.qforge.question.ws.OcrWsPushService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiAnalysisResultConsumerTest {

    @Mock
    private OcrWsPushService wsPushService;

    @Mock
    private QuestionAiTaskRepository questionAiTaskRepository;

    private AiAnalysisResultConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AiAnalysisResultConsumer(wsPushService, questionAiTaskRepository, new ObjectMapper());
    }

    @Test
    void shouldTruncateErrorMessageBeforePersistingFailedTask() {
        QuestionAiTask task = new QuestionAiTask();
        task.setTaskUuid("task-1");
        when(questionAiTaskRepository.findByTaskUuid("task-1")).thenReturn(Optional.of(task));
        String longErrorMessage = "E".repeat(2500);

        consumer.onAiAnalysisResult(new AiAnalysisResultEvent(
                "task-1",
                "question-1",
                "admin",
                false,
                null,
                null,
                null,
                longErrorMessage
        ));

        ArgumentCaptor<QuestionAiTask> taskCaptor = ArgumentCaptor.forClass(QuestionAiTask.class);
        verify(questionAiTaskRepository).updateById(taskCaptor.capture());
        QuestionAiTask updatedTask = taskCaptor.getValue();
        assertEquals("FAILED", updatedTask.getStatus());
        assertEquals(2048, updatedTask.getErrorMsg().length());
        assertEquals(longErrorMessage.substring(0, 2048), updatedTask.getErrorMsg());
    }

    @Test
    void shouldTruncateReasoningBeforePersistingSuccessTask() {
        QuestionAiTask task = new QuestionAiTask();
        task.setTaskUuid("task-2");
        when(questionAiTaskRepository.findByTaskUuid("task-2")).thenReturn(Optional.of(task));
        String longReasoning = "R".repeat(1500);

        consumer.onAiAnalysisResult(new AiAnalysisResultEvent(
                "task-2",
                "question-2",
                "admin",
                true,
                List.of("tagA"),
                new BigDecimal("0.33"),
                longReasoning,
                null
        ));

        ArgumentCaptor<QuestionAiTask> taskCaptor = ArgumentCaptor.forClass(QuestionAiTask.class);
        verify(questionAiTaskRepository).updateById(taskCaptor.capture());
        QuestionAiTask updatedTask = taskCaptor.getValue();
        assertEquals("SUCCESS", updatedTask.getStatus());
        assertEquals(1024, updatedTask.getReasoning().length());
        assertEquals(longReasoning.substring(0, 1024), updatedTask.getReasoning());
    }
}
