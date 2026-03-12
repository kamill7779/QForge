package io.github.kamill7779.qforge.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.question.client.OcrServiceClient;
import io.github.kamill7779.qforge.question.config.QForgeBusinessProperties;
import io.github.kamill7779.qforge.question.dto.QuestionPageResponse;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.redis.TaskStateRedisService;
import io.github.kamill7779.qforge.question.repository.AnswerAssetRepository;
import io.github.kamill7779.qforge.question.repository.AnswerRepository;
import io.github.kamill7779.qforge.question.repository.QuestionAiTaskRepository;
import io.github.kamill7779.qforge.question.repository.QuestionAssetRepository;
import io.github.kamill7779.qforge.question.repository.QuestionOcrTaskRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
import io.github.kamill7779.qforge.question.repository.QuestionTagRelRepository;
import io.github.kamill7779.qforge.question.repository.TagCategoryRepository;
import io.github.kamill7779.qforge.question.repository.TagRepository;
import io.github.kamill7779.qforge.question.service.QuestionCommandServiceImpl;
import io.github.kamill7779.qforge.question.service.QuestionSummaryQueryService;
import io.github.kamill7779.qforge.question.service.QuestionTagAssignmentService;
import io.github.kamill7779.qforge.question.validation.StemXmlValidator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class QuestionPagingVisibilityTest {

    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private AnswerAssetRepository answerAssetRepository;
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
    private QuestionTagAssignmentService questionTagAssignmentService;
    @Mock
    private QuestionSummaryQueryService questionSummaryQueryService;
    @Mock
    private StringRedisTemplate redis;

    @InjectMocks
    private QuestionCommandServiceImpl service;

    @Test
    void pageUserQuestionsShouldOnlyUseReadyDataset() {
        Question readyQuestion = readyQuestion(1L, "q-ready-1", "admin");

        when(questionRepository.countReadyByOwnerUser("admin")).thenReturn(1L);
        when(questionRepository.findReadyPageByOwnerUser("admin", 0, 20)).thenReturn(List.of(readyQuestion));
        when(answerRepository.findByQuestionIds(List.of(1L))).thenReturn(List.of());
        when(tagCategoryRepository.findEnabledMainCategories()).thenReturn(List.of());
        when(questionTagRelRepository.findByQuestionIds(List.of(1L))).thenReturn(List.of());

        QuestionPageResponse response = service.pageUserQuestions("admin", 1, 20);

        assertEquals(1L, response.total());
        assertFalse(response.hasMore());
        assertEquals(1, response.items().size());
        assertEquals("q-ready-1", response.items().get(0).questionUuid());
        assertEquals("READY", response.items().get(0).status());
        verify(questionRepository).countReadyByOwnerUser("admin");
        verify(questionRepository).findReadyPageByOwnerUser("admin", 0, 20);
        verify(questionRepository, never()).countByOwnerUser(anyString());
        verify(questionRepository, never()).findPageByOwnerUser(anyString(), anyInt(), anyInt());
    }

    private Question readyQuestion(Long id, String questionUuid, String ownerUser) {
        Question question = new Question();
        question.setId(id);
        question.setQuestionUuid(questionUuid);
        question.setOwnerUser(ownerUser);
        question.setStatus("READY");
        question.setStemText("<stem><p>已完成题目</p></stem>");
        question.setVisibility("PRIVATE");
        question.setDeleted(false);
        question.setDifficulty(BigDecimal.valueOf(2));
        question.setSource("未分类");
        question.setCreatedAt(LocalDateTime.of(2026, 3, 12, 9, 0, 0));
        question.setUpdatedAt(LocalDateTime.of(2026, 3, 12, 9, 5, 0));
        return question;
    }
}
