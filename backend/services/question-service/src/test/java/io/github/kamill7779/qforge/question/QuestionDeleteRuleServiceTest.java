package io.github.kamill7779.qforge.question;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.kamill7779.qforge.question.client.OcrServiceClient;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.repository.AnswerAssetRepository;
import io.github.kamill7779.qforge.question.repository.QuestionAiTaskRepository;
import io.github.kamill7779.qforge.question.repository.AnswerRepository;
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

@ExtendWith(MockitoExtension.class)
class QuestionDeleteRuleServiceTest {

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
    private AnswerAssetRepository answerAssetRepository;

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

    @InjectMocks
    private QuestionCommandServiceImpl questionCommandService;

    @Test
    void shouldDeleteDraftQuestionWhenStemNotConfirmedAndNoAnswer() {
        Question question = draftQuestion(10L, "q-uuid-10", null);
        when(questionRepository.findByQuestionUuidAndOwnerUser("q-uuid-10", "admin"))
                .thenReturn(Optional.of(question));

        questionCommandService.deleteQuestion("q-uuid-10", "admin");

        verify(questionOcrTaskRepository).deleteByQuestionUuid("q-uuid-10");
        verify(questionRepository).deleteById(10L);
    }

    @Test
    void shouldDeleteReadyQuestionWithAnswers() {
        Question question = draftQuestion(13L, "q-uuid-13", "already confirmed stem");
        question.setStatus("READY");
        when(questionRepository.findByQuestionUuidAndOwnerUser("q-uuid-13", "admin"))
                .thenReturn(Optional.of(question));

        questionCommandService.deleteQuestion("q-uuid-13", "admin");

        verify(answerAssetRepository).deleteByQuestionId(13L);
        verify(answerRepository).deleteByQuestionId(13L);
        verify(questionAssetRepository).softDeleteByQuestionId(13L);
        verify(questionTagRelRepository).deleteByQuestionId(13L);
        verify(questionOcrTaskRepository).deleteByQuestionUuid("q-uuid-13");
        verify(questionAiTaskRepository).deleteByQuestionUuid("q-uuid-13");
        verify(questionRepository).deleteById(13L);
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
