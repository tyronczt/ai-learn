package cn.tyron.llm.controller;

import cn.tyron.llm.chunking.TextChunkingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文本分块控制器
 */
@RestController
@RequestMapping("/api/chunking")
public class TextChunkingController {

    private final TextChunkingService textChunkingService;

    public TextChunkingController(TextChunkingService textChunkingService) {
        this.textChunkingService = textChunkingService;
    }

    /**
     * 固定大小分块接口
     */
    @GetMapping("/fixed")
    public Result<List<String>> fixedSizeChunk(
            @RequestParam String text,
            @RequestParam(defaultValue = "500") int chunkSize,
            @RequestParam(defaultValue = "50") int overlap) {
        return Result.success(textChunkingService.fixedSizeChunk(text, chunkSize, overlap));
    }

    /**
     * 递归分块
     */
    @GetMapping("/recursive")
    public Result<List<String>> recursiveChunk(
            @RequestParam String text,
            @RequestParam(defaultValue = "500") int maxChunkSize) {
        return Result.success(textChunkingService.recursiveChunk(text, maxChunkSize));
    }

    /**
     * Token 分块
     */
    @GetMapping("/token")
    public Result<List<String>> tokenChunk(
            @RequestParam String text,
            @RequestParam(defaultValue = "500") int chunkSize,
            @RequestParam(defaultValue = "50") int chunkOverlap) {
        return Result.success(textChunkingService.tokenChunk(text, chunkSize, chunkOverlap));
    }

    /**
     * 语义分块（基于 Embedding）
     */
    @GetMapping("/semantic")
    public Result<List<String>> semanticChunk(
            @RequestParam String text,
            @RequestParam(defaultValue = "0.5") double similarityThreshold,
            @RequestParam(defaultValue = "500") int maxTokensPerChunk) {
        return Result.success(textChunkingService.semanticChunk(text, similarityThreshold, maxTokensPerChunk));
    }

    /**
     * 混合语义分块
     */
    @GetMapping("/hybrid")
    public Result<List<String>> hybridSemanticChunk(
            @RequestParam String text,
            @RequestParam(defaultValue = "500") int chunkSize,
            @RequestParam(defaultValue = "50") int chunkOverlap,
            @RequestParam(defaultValue = "0.75") double similarityThreshold) {
        return Result.success(textChunkingService.hybridSemanticChunk(text, chunkSize, chunkOverlap, similarityThreshold));
    }

    /**
     * Markdown 按标题分块
     */
    @GetMapping("/markdown")
    public Result<Map<String, String>> markdownChunk(@RequestParam String markdown) {
        return Result.success(textChunkingService.markdownChunkByHeader(markdown));
    }

    /**
     * Agentic 分块（按任务类型）
     */
    @GetMapping("/agentic")
    public Result<List<String>> agenticChunk(
            @RequestParam String text,
            @RequestParam(defaultValue = "semantic_search") String task) {
        return Result.success(textChunkingService.agenticChunk(text, task));
    }

    /**
     * 计算语义相似度
     */
    @GetMapping("/similarity")
    public Result<Double> calculateSimilarity(
            @RequestParam String text1,
            @RequestParam String text2) {
        return Result.success(textChunkingService.calculateSentenceSimilarity(text1, text2));
    }

    /**
     * 生成 Embedding 向量
     */
    @GetMapping("/embedding")
    public Result<List<Double>> generateEmbedding(@RequestParam String text) {
        return Result.success(textChunkingService.generateEmbedding(text));
    }

    /**
     * 统一分块接口
     */
    @PostMapping("/chunk")
    public Result<List<String>> chunk(@RequestBody ChunkRequest request) {
        List<String> result;
        switch (request.getStrategy().toLowerCase()) {
            case "fixed":
                result = textChunkingService.fixedSizeChunk(request.getText(), request.getChunkSize(), request.getOverlap());
                break;
            case "recursive":
                result = textChunkingService.recursiveChunk(request.getText(), request.getChunkSize());
                break;
            case "token":
                result = textChunkingService.tokenChunk(request.getText(), request.getChunkSize(), request.getOverlap());
                break;
            case "semantic":
                result = textChunkingService.semanticChunk(request.getText(), request.getSimilarityThreshold(), request.getChunkSize());
                break;
            case "hybrid":
                result = textChunkingService.hybridSemanticChunk(request.getText(), request.getChunkSize(), request.getOverlap(), request.getSimilarityThreshold());
                break;
            case "agentic":
                result = textChunkingService.agenticChunk(request.getText(), request.getTask());
                break;
            default:
                result = textChunkingService.tokenChunk(request.getText(), request.getChunkSize(), request.getOverlap());
        }
        return Result.success(result);
    }

    // ========== 内部类 ==========

    public static class Result<T> {
        private int code;
        private String message;
        private T data;

        public static <T> Result<T> success(T data) {
            Result<T> result = new Result<>();
            result.code = 200;
            result.message = "success";
            result.data = data;
            return result;
        }

        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public T getData() { return data; }
        public void setData(T data) { this.data = data; }
    }

    public static class ChunkRequest {
        private String text;
        private String strategy = "token";
        private int chunkSize = 500;
        private int overlap = 50;
        private double similarityThreshold = 0.5;
        private String task = "semantic_search";

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        public int getOverlap() { return overlap; }
        public void setOverlap(int overlap) { this.overlap = overlap; }
        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
        public String getTask() { return task; }
        public void setTask(String task) { this.task = task; }
    }
}
