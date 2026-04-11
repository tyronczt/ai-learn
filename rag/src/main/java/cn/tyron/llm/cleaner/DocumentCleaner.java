package cn.tyron.llm.cleaner;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.ai.document.Document;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @description: 文档清洗
 * @author: tyron
 * @create: 2026-04-11
 **/
public class DocumentCleaner {
    public static List<Document> cleanDocuments(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return documents;
        }

        return documents.stream().map(doc -> {
            if (doc == null || doc.getText() == null) {
                return doc;
            }

            String text = doc.getText();

            // 1. 去掉多余空白字符（空格、制表符、换行等）
            text = text.replaceAll("\\s+", " ").trim();

            // 2. 去掉无意义的乱码或特殊符号
            text = text.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}\\n]", "");

            // 3. 可选：统一大小写
            // text = text.toLowerCase();

            // 4. 按换行拆分段落，去除重复段落
            String[] paragraphs = text.split("\\n+");
            Set<String> seen = new LinkedHashSet<>();
            for (String para : paragraphs) {
                String trimmed = para.trim();
                if (!trimmed.isEmpty()) {
                    seen.add(trimmed);
                }
            }

            text = String.join("\n", seen);

            return new Document(text);
        }).collect(Collectors.toList());
    }
}
