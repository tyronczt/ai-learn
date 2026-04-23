package cn.tyron.llm.embedding.milvus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Milvus 向量检索结果 DTO
 *
 * @author tyron
 * @create 2026-04-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Milvus 向量检索结果")
public class MilvusSearchResult {

    @Schema(description = "向量 ID")
    private Long id;

    @Schema(description = "文本内容")
    private String content;

    @Schema(description = "来源/类别")
    private String source;

    @Schema(description = "相似度分数（COSINE 度量下，值越大越相似）")
    private float score;

    @Schema(description = "距离值")
    private float distance;
}
