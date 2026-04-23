package cn.tyron.llm.embedding.milvus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Milvus 向量检索请求 DTO
 *
 * @author tyron
 * @create 2026-04-23
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Milvus 向量检索请求")
public class MilvusSearchRequest {

    @Schema(description = "查询文本", example = "退货政策是什么", requiredMode = Schema.RequiredMode.REQUIRED)
    private String query;

    @Schema(description = "返回结果数量", example = "5", defaultValue = "5")
    private int topK = 5;

    @Schema(description = "来源过滤条件（精确匹配，可选）", example = "退货政策")
    private String sourceFilter;

    @Schema(description = "过滤表达式（Milvus 标量过滤语法，可选）", example = "source == '退货政策'")
    private String filterExpression;
}
