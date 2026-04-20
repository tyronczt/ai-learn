package cn.tyron.llm.chunking;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DashScope 配置类
 * - EmbeddingModel: 手动配置
 * - ChatClient: 利用 Starter 自动配置的 ChatModel 创建
 */
@Configuration
public class DashScopeEmbeddingConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(apiKey)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(DashScopeApi dashScopeApi) {
        return new DashScopeEmbeddingModel(dashScopeApi);
    }

    /**
     * 利用 Starter 自动配置的 ChatModel 创建 ChatClient
     * 指定使用 dashScopeChatModel
     */
    @Bean
    public ChatClient chatClient(@Qualifier("dashScopeChatModel") ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
