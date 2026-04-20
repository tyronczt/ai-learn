package cn.tyron.llm.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @description: 从 chunk 到向量，再到相似度检索
 * @author: chenzt
 * @create: 2026-04-20
 **/
@Component
public class EmbeddingSearchService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingSearchService.class);

    /**
     * 使用 SiliconFlow Embedding 模型（Qwen/Qwen3-Embedding-8B）
     */
    @Autowired
    @Qualifier("siliconflowEmbeddingModel")
    private EmbeddingModel embeddingModel;

    /**
     * 向量化文本（批量）
     */
    public List<double[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(texts, EmbeddingOptions.builder().build())
            );

            return response.getResults().stream()
                    .map(embedding -> {
                        float[] floatArray = embedding.getOutput();
                        double[] doubleArray = new double[floatArray.length];
                        for (int i = 0; i < floatArray.length; i++) {
                            doubleArray[i] = floatArray[i];
                        }
                        return doubleArray;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("向量化失败, textCount={}", texts.size(), e);
            throw new RuntimeException("向量化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 向量化单个文本
     */
    public double[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new double[0];
        }
        List<double[]> results = embed(List.of(text));
        return results.isEmpty() ? new double[0] : results.get(0);
    }

    /**
     * 计算余弦相似度
     */
    public double cosineSimilarity(double[] vecA, double[] vecB) {
        if (vecA == null || vecB == null) {
            throw new IllegalArgumentException("向量不能为 null");
        }
        if (vecA.length != vecB.length) {
            throw new IllegalArgumentException("向量维度不一致: " + vecA.length + " vs " + vecB.length);
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vecA.length; i++) {
            dotProduct += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }

        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (normA * normB);
    }

    /**
     * 批量向量化（分页 + 并发 + 重试）
     */
    public List<double[]> embedBatch(List<String> texts, int batchSize, int maxConcurrency) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        ExecutorService executor = null;
        try {
            executor = Executors.newFixedThreadPool(maxConcurrency);
            List<Future<List<double[]>>> futures = new ArrayList<>();

            for (int i = 0; i < texts.size(); i += batchSize) {
                int end = Math.min(i + batchSize, texts.size());
                List<String> batch = texts.subList(i, end);

                Future<List<double[]>> future = executor.submit(() -> {
                    int retries = 3;
                    int backoff = 1000;

                    for (int attempt = 0; attempt < retries; attempt++) {
                        try {
                            return embed(batch);
                        } catch (Exception e) {
                            if (attempt == retries - 1) {
                                throw e;
                            }
                            log.warn("向量化重试, attempt={}, error={}", attempt + 1, e.getMessage());
                            try {
                                Thread.sleep(backoff);
                                backoff *= 2;
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("重试被中断", ie);
                            }
                        }
                    }
                    return Collections.emptyList();
                });
                futures.add(future);
            }

            List<double[]> results = new ArrayList<>();
            for (Future<List<double[]>> future : futures) {
                try {
                    results.addAll(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("等待向量化结果时被中断", e);
                } catch (ExecutionException e) {
                    throw new RuntimeException("向量化执行异常: " + e.getCause().getMessage(), e.getCause());
                }
            }

            return results;
        } finally {
            if (executor != null) {
                executor.shutdown();
            }
        }
    }

    /**
     * 相似度检索
     */
    public List<SearchResult> search(List<Chunk> chunks, List<double[]> chunkVectors, String query, int topK) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }
        if (chunkVectors == null || chunkVectors.size() != chunks.size()) {
            throw new IllegalArgumentException("chunk 向量数量与 chunk 数量不匹配");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("查询语句不能为空");
        }

        double[] queryVector = embed(query);

        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            double similarity = cosineSimilarity(queryVector, chunkVectors.get(i));
            results.add(new SearchResult(chunks.get(i), similarity));
        }

        // 按相似度降序排序
        results.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));

        return results.subList(0, Math.min(topK, results.size()));
    }

    /**
     * 执行完整检索流程
     */
    public List<SearchResult> searchByQuery(String query, int topK) {
        // 1. 准备 chunks（带元数据）
        List<Chunk> chunks = prepareChunks();

        // 2. 批量向量化所有 chunks
        List<String> texts = chunks.stream().map(Chunk::getContent).collect(Collectors.toList());
        List<double[]> chunkVectors = embedBatch(texts, 20, 3);

        // 3. 检索
        return search(chunks, chunkVectors, query, topK);
    }

    /**
     * 准备测试 chunks
     */
    public List<Chunk> prepareChunks() {
        List<Chunk> chunks = new ArrayList<>();
        chunks.add(new Chunk("1", "七天无理由退货政策：商品在签收后7天内，如不满意可申请退货", "退货政策"));
        chunks.add(new Chunk("2", "生鲜类商品不支持七天无理由退货，因其属于鲜活易腐类商品", "生鲜退货政策"));
        chunks.add(new Chunk("3", "退货流程：进入我的订单 -> 申请退货 -> 填写退货原因 -> 等待审核", "退货流程"));
        chunks.add(new Chunk("4", "退款将在退货成功后1-3个工作日内退回原支付账户", "退款时效"));
        chunks.add(new Chunk("5", "质量问题退货：需提供照片证明，运费由卖家承担", "质量问题退货"));
        return chunks;
    }
}
