package cn.tyron.llm.controller;

import cn.tyron.llm.embedding.EmbeddingSearchService;
import cn.tyron.llm.embedding.SearchResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * @description: Embedding 相似度检索测试接口
 * @author: chenzt
 * @create: 2026-04-20
 **/
@Tag(name = "Embedding检索", description = "向量相似度检索相关接口")
@RestController
@RequestMapping("/rag/embedding")
@Validated
public class EmbeddingSearchController {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingSearchController.class);

    private static final String SILICONFLOW_URL = "https://api.siliconflow.cn/v1/embeddings";
    private static final String SILICONFLOW_API_KEY = "sk-yrdxaaxehbwwvjxdzknnvpmhucghixmnmpdsabltekuaegbd";
    private static final String MODEL = "Qwen/Qwen3-Embedding-8B";

    private final RestClient restClient = RestClient.create();

    @Autowired
    private EmbeddingSearchService embeddingSearchService;

    /**
     * 直接调用 SiliconFlow Embedding API
     */
    @Operation(summary = "SiliconFlow Embedding", description = "直接调用 SiliconFlow API 获取文本向量")
    @PostMapping(value = "/embed", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> embed(@Parameter(description = "待向量化的文本列表", required = true)
                                       @RequestBody Map<String, Object> request) {
        log.info("收到 embedding 请求: {}", request);

        @SuppressWarnings("unchecked")
        List<String> texts = (List<String>) request.get("input");

        Map<String, Object> apiRequest = Map.of(
                "model", MODEL,
                "input", texts,
                "encoding_format", "float"
        );

        Map<String, Object> response = restClient.post()
                .uri(SILICONFLOW_URL)
                .header("Authorization", "Bearer " + SILICONFLOW_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(apiRequest)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {});

        log.info("Embedding 成功, textCount={}", texts.size());
        return response;
    }

    /**
     * 相似度检索
     */
    @Operation(summary = "相似度检索", description = "根据查询语句检索最相似的 chunks")
    @GetMapping("/search")
    public List<SearchResult> search(
            @Parameter(description = "查询语句", required = true)
            @RequestParam @NotBlank(message = "查询语句不能为空") String query,
            @Parameter(description = "返回结果数量")
            @RequestParam(defaultValue = "3") @Min(value = 1, message = "topK 最小值为 1")
            @Max(value = 100, message = "topK 最大值为 100") int topK) {
        log.info("收到检索请求, query={}, topK={}", query, topK);
        List<SearchResult> results = embeddingSearchService.searchByQuery(query, topK);
        log.info("检索完成, 结果数量={}", results.size());
        return results;
    }

    /**
     * 参数校验异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /**
     * 向量化/检索异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException e) {
        log.error("运行时错误: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
    }

    /**
     * 参数校验异常（来自 @Validated）
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolation(jakarta.validation.ConstraintViolationException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
