package io.github.kamill7779.qforge.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.kamill7779.qforge.internal.api.CreateQuestionFromGaokaoRequest;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.entity.QuestionAsset;
import io.github.kamill7779.qforge.question.repository.AnswerAssetRepository;
import io.github.kamill7779.qforge.question.repository.AnswerRepository;
import io.github.kamill7779.qforge.question.repository.QuestionAssetRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
import io.github.kamill7779.qforge.question.service.GaokaoQuestionCreateService;
import io.github.kamill7779.qforge.question.service.QuestionSummaryQueryService;
import io.github.kamill7779.qforge.question.service.QuestionTagAssignmentService;
import java.util.List;
import org.junit.jupiter.api.Test;

class GaokaoQuestionCreateServiceDataUriTest {

    @Test
    void createShouldPreferDataUriOverStorageRef() {
        QuestionRepository questionRepository = mock(QuestionRepository.class);
        AnswerRepository answerRepository = mock(AnswerRepository.class);
        QuestionAssetRepository questionAssetRepository = mock(QuestionAssetRepository.class);
        AnswerAssetRepository answerAssetRepository = mock(AnswerAssetRepository.class);
        QuestionTagAssignmentService tagAssignmentService = mock(QuestionTagAssignmentService.class);
        QuestionSummaryQueryService questionSummaryQueryService = mock(QuestionSummaryQueryService.class);

        doAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(101L);
            return question;
        }).when(questionRepository).save(any(Question.class));

        final QuestionAsset[] captured = new QuestionAsset[1];
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            return captured[0];
        }).when(questionAssetRepository).save(any(QuestionAsset.class));

        GaokaoQuestionCreateService service = new GaokaoQuestionCreateService(
                questionRepository,
                answerRepository,
                questionAssetRepository,
                answerAssetRepository,
                tagAssignmentService,
                questionSummaryQueryService
        );

        CreateQuestionFromGaokaoRequest.AssetEntry asset = new CreateQuestionFromGaokaoRequest.AssetEntry();
        asset.setAssetType("INLINE_IMAGE");
        asset.setRefKey("img-1");
        asset.setStorageRef("C:/tmp/legacy.png");
        asset.setDataUri("data:image/png;base64,Zm9v");

        CreateQuestionFromGaokaoRequest request = new CreateQuestionFromGaokaoRequest();
        request.setOwnerUser("u1");
        request.setStemText("<stem><img ref=\"img-1\"/></stem>");
        request.setStemAssets(List.of(asset));

        service.create(request);

        assertEquals("Zm9v", captured[0].getImageData());
        assertEquals("image/png", captured[0].getMimeType());
        verify(answerRepository, never()).save(any());
    }
}
