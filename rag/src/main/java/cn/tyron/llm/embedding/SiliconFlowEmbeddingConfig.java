package cn.tyron.llm.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
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
            @Value("${spring.ai.siliconflow.embedding.options.model}") String model,
            @Value("${spring.ai.siliconflow.embedding.connect-timeout:15s}") Duration connectTimeout,
            @Value("${spring.ai.siliconflow.embedding.read-timeout:90s}") Duration readTimeout) {
        this.model = model;

        // 配置 HTTP 超时，避免网络抖动时无限等待
        // 注意：SiliconFlow API 在批量请求时响应可能较慢，读取超时设为可配置值
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(connectTimeout)
                        .withReadTimeout(readTimeout)
        );

        this.restClient = RestClient.builder()
                .baseUrl(endpoint)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("SiliconFlow embedding 客户端初始化完成 (connectTimeout={}, readTimeout={}, model={})",
                connectTimeout, readTimeout, model);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        Instant start = Instant.now();
        int textCount = request.getInstructions().size();
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "input", request.getInstructions(),
                    "encoding_format", "float"
            );
            log.info("SiliconFlow embedding 请求开始, model={}, textCount={}", model, textCount);

            Map<String, Object> response = restClient.post()
                    .uri("/v1/embeddings")
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            long elapsedMs = Duration.between(start, Instant.now()).toMillis();
            log.info("SiliconFlow embedding 请求完成, textCount={}, elapsed={}ms", textCount, elapsedMs);

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
        } catch (ResourceAccessException e) {
            Throwable rootCause = getRootCause(e);
            if (rootCause instanceof ReadTimeoutException) {
                log.warn("SiliconFlow embedding 读取超时, model={}, timeoutCause={}", model, rootCause.getClass().getSimpleName());
                throw new RuntimeException("Embedding 读取超时，请增大 read-timeout 或稍后重试", e);
            }
            log.warn("SiliconFlow embedding 网络调用失败: {}", e.getMessage());
            throw new RuntimeException("Embedding 网络调用失败，请稍后重试", e);
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

    private Throwable getRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
