package cn.tyron.llm.chunking;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DashScope Embedding 配置类
 */
@Configuration
public class DashScopeEmbeddingConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Bean
    public EmbeddingModel embeddingModel() {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(apiKey)
                .build();
        return new DashScopeEmbeddingModel(dashScopeApi);
    }
}
