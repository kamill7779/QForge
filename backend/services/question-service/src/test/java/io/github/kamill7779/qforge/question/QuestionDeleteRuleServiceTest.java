package io.github.kamill7779.qforge.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.kamill7779.qforge.question.client.OcrServiceClient;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.exception.BusinessValidationException;
import io.github.kamill7779.qforge.question.repository.AnswerRepository;
import io.github.kamill7779.qforge.question.repository.QuestionOcrTaskRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
import io.github.kamill7779.qforge.question.repository.QuestionTagRelRepository;
import io.github.kamill7779.qforge.question.repository.TagCategoryRepository;
import io.github.kamill7779.qforge.question.repository.TagRepository;
import io.github.kamill7779.qforge.question.service.QuestionCommandServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionDeleteRuleServiceTest {

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private QuestionOcrTaskRepository questionOcrTaskRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private TagCategoryRepository tagCategoryRepository;

    @Mock
    private QuestionTagRelRepository questionTagRelRepository;

    @Mock
    private OcrServiceClient ocrServiceClient;

    @InjectMocks
    private QuestionCommandServiceImpl questionCommandService;

    @Test
    void shouldDeleteDraftQuestionWhenStemNotConfirmedAndNoAnswer() {
        Question question = draftQuestion(10L, "q-uuid-10", null);
        when(questionRepository.findByQuestionUuidAndOwnerUser("q-uuid-10", "admin"))
                .thenReturn(Optional.of(question));
        when(answerRepository.countByQuestionId(10L)).thenReturn(0L);

        questionCommandService.deleteDraftQuestion("q-uuid-10", "admin");

        verify(questionOcrTaskRepository).deleteByQuestionUuid("q-uuid-10");
        verify(questionRepository).deleteById(10L);
    }

    @Test
    void shouldDeletePendingAnswerQuestionWhenNoAnswerYet() {
        Question question = draftQuestion(13L, "q-uuid-13", "already confirmed stem");
        when(questionRepository.findByQuestionUuidAndOwnerUser("q-uuid-13", "admin"))
                .thenReturn(Optional.of(question));
        when(answerRepository.countByQuestionId(13L)).thenReturn(0L);

        questionCommandService.deleteDraftQuestion("q-uuid-13", "admin");

        verify(questionOcrTaskRepository).deleteByQuestionUuid("q-uuid-13");
        verify(questionRepository).deleteById(13L);
    }

    @Test
    void shouldRejectDeleteWhenQuestionAlreadyReady() {
        Question question = draftQuestion(11L, "q-uuid-11", "confirmed stem");
        question.setStatus("READY");
        when(questionRepository.findByQuestionUuidAndOwnerUser("q-uuid-11", "admin"))
                .thenReturn(Optional.of(question));
        when(answerRepository.countByQuestionId(11L)).thenReturn(0L);

        BusinessValidationException ex = assertThrows(
                BusinessValidationException.class,
                () -> questionCommandService.deleteDraftQuestion("q-uuid-11", "admin")
        );
        assertEquals("QUESTION_DELETE_NOT_ALLOWED", ex.getCode());
        verify(questionRepository, never()).deleteById(11L);
    }

    @Test
    void shouldRejectDeleteWhenQuestionAlreadyHasAnswer() {
        Question question = draftQuestion(12L, "q-uuid-12", null);
        when(questionRepository.findByQuestionUuidAndOwnerUser("q-uuid-12", "admin"))
                .thenReturn(Optional.of(question));
        when(answerRepository.countByQuestionId(12L)).thenReturn(1L);

        BusinessValidationException ex = assertThrows(
                BusinessValidationException.class,
                () -> questionCommandService.deleteDraftQuestion("q-uuid-12", "admin")
        );
        assertEquals("QUESTION_DELETE_NOT_ALLOWED", ex.getCode());
        verify(questionRepository, never()).deleteById(12L);
    }

    private Question draftQuestion(Long id, String questionUuid, String stemText) {
        Question question = new Question();
        question.setId(id);
        question.setQuestionUuid(questionUuid);
        question.setOwnerUser("admin");
        question.setStatus("DRAFT");
        question.setStemText(stemText);
        return question;
    }
}
