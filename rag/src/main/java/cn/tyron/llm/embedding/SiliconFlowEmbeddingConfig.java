package cn.tyron.llm.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * SiliconFlow Embedding 模型实现
 * 使用 OpenAI 兼容 API 调用 SiliconFlow（Qwen/Qwen3-Embedding-8B）
 */
@Component("siliconflowEmbeddingModel")
public class SiliconFlowEmbeddingConfig extends AbstractEmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(SiliconFlowEmbeddingConfig.class);

    private final RestClient restClient;
    private final String model;

    public SiliconFlowEmbeddingConfig(
            @Value("${spring.ai.siliconflow.api-key}") String apiKey,
            @Value("${spring.ai.siliconflow.endpoint}") String endpoint,
            @Value("${spring.ai.siliconflow.embedding.options.model}") String model) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "input", request.getInstructions(),
                    "encoding_format", "float"
            );

            Map<String, Object> response = restClient.post()
                    .uri("/v1/embeddings")
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");

            List<Embedding> embeddings = data.stream()
                    .map(item -> {
                        @SuppressWarnings("unchecked")
                        List<Number> embeddingList = (List<Number>) item.get("embedding");
                        float[] floats = new float[embeddingList.size()];
                        for (int i = 0; i < embeddingList.size(); i++) {
                            floats[i] = embeddingList.get(i).floatValue();
                        }
                        return new Embedding(floats, ((Number) item.get("index")).intValue());
                    })
                    .toList();

            return new EmbeddingResponse(embeddings);
        } catch (Exception e) {
            log.error("SiliconFlow embedding 调用失败", e);
            throw new RuntimeException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public float[] embed(Document document) {
        // 获取文档内容进行嵌入
        String content = document.getText();
        return embed(content);
    }
}
