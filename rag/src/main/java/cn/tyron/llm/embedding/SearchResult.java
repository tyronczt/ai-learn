package cn.tyron.llm.embedding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description: 检索结果
 * @author: tyron
 * @create: 2026-04-20
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    private Chunk chunk;
    private double similarity;
}
