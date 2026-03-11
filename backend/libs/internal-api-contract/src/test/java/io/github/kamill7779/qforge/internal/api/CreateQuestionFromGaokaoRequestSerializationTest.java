package io.github.kamill7779.qforge.internal.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateQuestionFromGaokaoRequestSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void assetEntryShouldRoundTripDataUri() throws Exception {
        CreateQuestionFromGaokaoRequest.AssetEntry asset = new CreateQuestionFromGaokaoRequest.AssetEntry();
        asset.setAssetType("INLINE_IMAGE");
        asset.setStorageRef("cos://qforge-2026-1304896342/ap-shanghai/gaokao/assets/paper-1/img-1.png");
        asset.setDataUri("data:image/png;base64,Zm9v");
        asset.setRefKey("img-1");

        CreateQuestionFromGaokaoRequest request = new CreateQuestionFromGaokaoRequest();
        request.setOwnerUser("u1");
        request.setStemText("<stem />");
        request.setStemAssets(List.of(asset));

        String json = objectMapper.writeValueAsString(request);
        CreateQuestionFromGaokaoRequest restored =
                objectMapper.readValue(json, CreateQuestionFromGaokaoRequest.class);

        assertEquals("data:image/png;base64,Zm9v", restored.getStemAssets().get(0).getDataUri());
    }
}
