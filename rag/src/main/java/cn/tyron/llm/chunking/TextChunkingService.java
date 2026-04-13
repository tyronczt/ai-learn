package cn.tyron.llm.chunking;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 文本分块服务
 * 演示多种文本分块策略：固定大小、递归、Token级别、语义、文档结构、Agentic
 */
@Service
public class TextChunkingService {

    private final EmbeddingModel embeddingModel;

    public TextChunkingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    // ========== 1. 固定大小分块（按字符） ==========
    public List<String> fixedSizeChunk(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;

        int step = chunkSize - overlap;
        if (step <= 0) throw new IllegalArgumentException("overlap must be less than chunkSize");

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += step;
        }
        return chunks;
    }

    // ========== 2. 递归分块（按分隔符优先级） ==========
    private static final List<String> SEPARATORS = Arrays.asList("\n\n", "\n", "。", ". ", " ");

    public List<String> recursiveChunk(String text, int maxChunkSize) {
        List<String> result = new ArrayList<>();
        if (text.length() <= maxChunkSize) {
            result.add(text);
            return result;
        }

        String bestSeparator = null;
        int bestIndex = -1;
        for (String sep : SEPARATORS) {
            int index = text.indexOf(sep);
            if (index > 0 && index < maxChunkSize) {
                bestSeparator = sep;
                bestIndex = index;
                break;
            }
        }

        if (bestSeparator == null) {
            result.add(text.substring(0, maxChunkSize));
            result.addAll(recursiveChunk(text.substring(maxChunkSize), maxChunkSize));
        } else {
            String first = text.substring(0, bestIndex + bestSeparator.length());
            String remaining = text.substring(bestIndex + bestSeparator.length());
            result.add(first);
            result.addAll(recursiveChunk(remaining, maxChunkSize));
        }
        return result;
    }

    // ========== 3. Token 级别分块 ==========
    public List<String> tokenChunk(String text, int chunkSize) {
        if (text == null || text.isEmpty()) return Collections.emptyList();

        org.springframework.ai.transformer.splitter.TokenTextSplitter splitter =
                new org.springframework.ai.transformer.splitter.TokenTextSplitter(
                        chunkSize, 50, 20, 100, true);

        org.springframework.ai.document.Document document = new org.springframework.ai.document.Document(text);
        List<org.springframework.ai.document.Document> splitDocs =
                splitter.split(Collections.singletonList(document));

        return splitDocs.stream()
                .map(org.springframework.ai.document.Document::getText)
                .collect(java.util.stream.Collectors.toList());
    }

    public List<String> tokenChunk(String text, int chunkSize, int chunkOverlap) {
        List<String> baseChunks = tokenChunk(text, chunkSize);
        if (baseChunks.size() <= 1) return baseChunks;

        List<String> result = new ArrayList<>();
        result.add(baseChunks.get(0));

        for (int i = 1; i < baseChunks.size(); i++) {
            String previous = baseChunks.get(i - 1);
            String current = baseChunks.get(i);
            String overlapText = extractOverlap(previous, chunkOverlap);
            result.add(overlapText + current);
        }
        return result;
    }

    private String extractOverlap(String text, int overlapTokens) {
        int charEstimate = overlapTokens * 4;
        if (text.length() <= charEstimate) return text;
        return text.substring(Math.max(0, text.length() - charEstimate));
    }

    // ========== 4. 语义分块（基于 Embedding） ==========
    public List<String> semanticChunk(String text, double similarityThreshold, int maxTokensPerChunk) {
        List<String> sentences = splitIntoSentences(text);
        if (sentences.isEmpty()) return Collections.emptyList();

        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int currentTokenCount = 0;

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            int sentenceTokens = estimateTokenCount(sentence);

            if (currentTokenCount + sentenceTokens > maxTokensPerChunk && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
                currentTokenCount = 0;
            }

            if (currentChunk.length() > 0 && i > 0) {
                double similarity = calculateSentenceSimilarity(sentences.get(i - 1), sentence);
                if (similarity < similarityThreshold) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                    currentTokenCount = 0;
                }
            }

            if (currentChunk.length() > 0) {
                currentChunk.append(" ");
            }
            currentChunk.append(sentence);
            currentTokenCount += sentenceTokens;
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        return chunks;
    }

    public List<String> hybridSemanticChunk(String text, int chunkSize, int chunkOverlap, double similarityThreshold) {
        List<String> tokenChunks = tokenChunk(text, chunkSize);
        if (tokenChunks.size() <= 1) return tokenChunks;

        List<String> mergedChunks = new ArrayList<>();
        mergedChunks.add(tokenChunks.get(0));

        for (int i = 1; i < tokenChunks.size(); i++) {
            String current = tokenChunks.get(i);
            String previous = mergedChunks.get(mergedChunks.size() - 1);

            int combinedTokens = estimateTokenCount(previous + " " + current);
            double similarity = calculateSentenceSimilarity(previous, current);

            if (combinedTokens <= chunkSize * 1.2 && similarity >= similarityThreshold) {
                mergedChunks.set(mergedChunks.size() - 1, previous + " " + current);
            } else {
                mergedChunks.add(current);
            }
        }
        return mergedChunks;
    }

    // ========== 5. 基于文档结构分块 ==========
    public Map<String, String> markdownChunkByHeader(String markdown) {
        Map<String, String> chunks = new LinkedHashMap<>();
        java.util.regex.Pattern headerPattern =
                java.util.regex.Pattern.compile("^(#{1,6})\\s+(.+)$", java.util.regex.Pattern.MULTILINE);

        String[] lines = markdown.split("\n");
        String currentHeader = "Introduction";
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            var matcher = headerPattern.matcher(line);
            if (matcher.find()) {
                if (currentContent.length() > 0) {
                    chunks.put(currentHeader, currentContent.toString().trim());
                }
                currentHeader = matcher.group(2);
                currentContent = new StringBuilder();
            } else {
                currentContent.append(line).append("\n");
            }
        }
        if (currentContent.length() > 0) {
            chunks.put(currentHeader, currentContent.toString().trim());
        }
        return chunks;
    }

    // ========== 6. Agentic 分块 ==========
    public List<String> agenticChunk(String text, String task) {
        if ("summarization".equalsIgnoreCase(task)) {
            return tokenChunk(text, 800);
        } else if ("qa".equalsIgnoreCase(task)) {
            return tokenChunk(text, 300);
        } else if ("semantic_search".equalsIgnoreCase(task)) {
            return hybridSemanticChunk(text, 500, 75, 0.75);
        } else {
            return tokenChunk(text, 500);
        }
    }

    // ========== Embedding 语义相似度 ==========

    /**
     * 计算两个文本段的语义相似度（使用 DashScope text-embedding-v2）
     */
    public double calculateSentenceSimilarity(String text1, String text2) {
        try {
            float[] embedding1 = embeddingModel.embed(text1);
            float[] embedding2 = embeddingModel.embed(text2);
            if (embedding1 == null || embedding2 == null || embedding1.length == 0 || embedding2.length == 0) {
                return calculateKeywordSimilarity(text1, text2);
            }
            return cosineSimilarityFloat(embedding1, embedding2);
        } catch (Exception e) {
            return calculateKeywordSimilarity(text1, text2);
        }
    }

    /**
     * 生成文本的 embedding 向量
     */
    public List<Double> generateEmbedding(String text) {
        try {
            float[] embedding = embeddingModel.embed(text);
            List<Double> result = new ArrayList<>(embedding.length);
            for (float v : embedding) {
                result.add((double) v);
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ========== 辅助方法 ==========

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        String[] parts = text.split("[。！？.!?\n]+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    public int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        long chineseChars = text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FA5).count();
        long otherChars = text.length() - chineseChars;
        return (int) Math.ceil(chineseChars / 1.5 + otherChars / 4.0);
    }

    private double calculateKeywordSimilarity(String text1, String text2) {
        Set<String> words1 = new HashSet<>(Arrays.asList(text1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(text2.split("\\s+")));
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }

    public double cosineSimilarityFloat(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException("Vectors must have same dimension");
        }
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2) + 1e-10);
    }
}
