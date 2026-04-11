package cn.tyron.llm.controller;

import cn.tyron.llm.cleaner.DocumentCleaner;
import cn.tyron.llm.reader.DocumentReaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @description: rag文件读取
 * @author: tyron
 * @create: 2026-04-11
 **/
@RestController
@RequestMapping("/rag")
public class RagReaderController {
    private static final Logger log = LoggerFactory.getLogger(RagReaderController.class);
    
    @Autowired
    private DocumentReaderService documentReaderService;

    @RequestMapping("/read")
    public String read(String filePaths) {
        // 支持多个文件路径，使用逗号分隔
        String[] paths = filePaths.split(",");
        List<Document> allDocuments = new ArrayList<>();
        
        for (String path : paths) {
            String trimmedPath = path.trim();
            if (trimmedPath.isEmpty()) {
                continue;
            }
            
            try {
                List<Document> documents = documentReaderService.read(new File(trimmedPath));
                allDocuments.addAll(documents);
                log.info("文件 [{}] 读取成功，文档数量: {}", trimmedPath, documents.size());
            } catch (IOException e) {
                log.error("读取文件 [{}] 失败: {}", trimmedPath, e.getMessage());
                throw new RuntimeException("读取文件失败: " + trimmedPath, e);
            }
        }

        log.info("总文档数量: {}", allDocuments.size());
        StringBuffer sb = new StringBuffer();
        for (Document document : allDocuments) {
            sb.append(document.getText());
            log.info("文档内容: {}", document.getText());
            log.info("文档元数据: {}", document.getMetadata());
            log.info("========");
            sb.append("========================");
        }
        return sb.toString();
    }

    @RequestMapping("/readHtmlFromUrl")
    public String readHtmlFromUrl(String url) {
        try {
            log.info("开始从URL读取HTML: {}", url);
            List<Document> documents = documentReaderService.readHtmlFromUrl(url);
            log.info("URL读取成功，文档数量: {}", documents.size());
            
            StringBuffer sb = new StringBuffer();
            for (Document document : documents) {
                sb.append(document.getText());
                log.info("文档内容: {}", document.getText());
                log.info("文档元数据: {}", document.getMetadata());
                log.info("========");
                sb.append("========================");
            }
            return sb.toString();
        } catch (IOException e) {
            log.error("从URL读取HTML失败: {}", e.getMessage());
            throw new RuntimeException("从URL读取HTML失败: " + url, e);
        }
    }
}