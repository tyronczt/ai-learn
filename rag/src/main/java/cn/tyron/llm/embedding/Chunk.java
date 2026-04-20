package cn.tyron.llm.embedding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description: Chunk 元数据
 * @author: chenzt
 * @create: 2026-04-20
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Chunk {
    private String id;
    private String content;
    private String source;
}
