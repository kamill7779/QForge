package io.github.kamill7779.qforge.gaokaoanalysis.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.kamill7779.qforge.gaokaoanalysis.config.QForgeAnalysisProperties;
import io.github.kamill7779.qforge.gaokaoanalysis.config.QdrantProperties;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.BuildVectorRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

class VectorServiceImplTest {

    private HttpServer server;
    private final List<String> requestUris = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handleRequest);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void buildQuestionVectorsShouldWaitForQdrantUpsertCompletion() {
        VectorServiceImpl service = new VectorServiceImpl(
                embeddingModelProvider(),
                analysisProperties(),
                qdrantProperties()
        );

        BuildVectorRequest request = new BuildVectorRequest();
        request.setQuestionId(42L);
        request.setQuestionUuid("q-42");
        request.setStemText("函数的单调性");
        request.setNormalizedStemText("函数单调性");
        request.setAnalysisSummaryText("求导后判断符号");
        request.setQuestionTypeCode("CHOICE");
        request.setDifficultyLevel("MEDIUM");

        service.buildQuestionVectors(request);

        assertTrue(
                requestUris.stream().anyMatch(uri -> uri.contains("/collections/questions/points?wait=true")),
                "expected upsert request to include wait=true"
        );
        assertTrue(
                requestUris.stream().noneMatch(uri -> uri.equals("GET /collections/questions")),
                "expected collection creation to avoid GET-then-PUT preflight"
        );
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        requestUris.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
        String path = exchange.getRequestURI().getPath();
        String responseBody;
        int status;
        if (path.equals("/collections/questions") && "GET".equals(exchange.getRequestMethod())) {
            status = 404;
            responseBody = "{\"status\":\"not_found\"}";
        } else if (path.equals("/collections/questions") && "PUT".equals(exchange.getRequestMethod())) {
            status = 200;
            responseBody = "{\"result\":true}";
        } else if (path.equals("/collections/questions/points") && "PUT".equals(exchange.getRequestMethod())) {
            status = 200;
            responseBody = "{\"result\":{\"status\":\"acknowledged\"}}";
        } else {
            status = 200;
            responseBody = "{\"result\":[]}";
        }
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private ObjectProvider<EmbeddingModel> embeddingModelProvider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<EmbeddingModel> provider = mock(ObjectProvider.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(provider.getIfAvailable()).thenReturn(embeddingModel);
        when(embeddingModel.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(new float[]{0.1f, 0.2f});
        return provider;
    }

    private QForgeAnalysisProperties analysisProperties() {
        QForgeAnalysisProperties properties = new QForgeAnalysisProperties();
        properties.setEmbeddingDimension(2);
        properties.setMaxSimilarResults(4);
        return properties;
    }

    private QdrantProperties qdrantProperties() {
        QdrantProperties properties = new QdrantProperties();
        properties.setHost("127.0.0.1");
        properties.setPort(server.getAddress().getPort());
        properties.setQuestionCollection("questions");
        properties.setChunkCollection("chunks");
        return properties;
    }
}
