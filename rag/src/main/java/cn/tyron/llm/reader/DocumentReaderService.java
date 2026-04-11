package cn.tyron.llm.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.jsoup.JsoupDocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * @description: 文档读取器 - 统一处理多种格式文档的读取
 * @author: tyron
 * @create: 2026-04-10
 **/
@Service
public class DocumentReaderService {
    private static final Logger log = LoggerFactory.getLogger(DocumentReaderService.class);

    /**
     * 根据文件类型读取文档
     */
    public List<Document> read(File file) throws IOException {
        String fileName = file.getName().toLowerCase();
        Resource resource = new FileSystemResource(file);

        if (fileName.endsWith(".pdf")) {
            return readPdf(resource, file);
        } else if (fileName.endsWith(".json")) {
            return readJson(resource);
        } else if (fileName.endsWith(".txt") || fileName.endsWith(".tex") || fileName.endsWith(".text")) {
            return readText(resource);
        } else if (fileName.endsWith(".md")) {
            return readMarkdown(resource, file);
        } else if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return readHtml(resource, file);
        } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
            return readTika(resource);
        } else if (fileName.endsWith(".ppt") || fileName.endsWith(".pptx")) {
            return readTika(resource);
        } else {
            throw new IllegalArgumentException("不支持的文件类型: " + fileName);
        }
    }

    /**
     * 读取 PDF 文件
     * 该方法用于从给定的资源中读取PDF文件内容，并返回文档列表
     *
     * @param resource PDF文件资源对象，包含PDF文件的路径和其他相关信息
     * @param file     PDF文件对象，表示要读取的本地文件
     * @return List<Document> 返回一个文档列表，每个文档代表PDF中的一页内容
     */
    private List<Document> readPdf(Resource resource, File file) {
        // 创建PDF文档读取器配置对象，设置页面边距和每页文档数等参数
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageTopMargin(50)        // 设置页面上边距为50
                .withPageBottomMargin(50)     // 设置页面下边距为50
                .withPagesPerDocument(1)      // 每个文档包含1页
                .withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder()
                        .withNumberOfTopTextLinesToDelete(0)  // 设置不删除顶部文本行
                        .build())
                .build();

        try {
            // 优先尝试使用段落PDF文档读取器（需要PDF有目录结构）
            ParagraphPdfDocumentReader pdfReader = new ParagraphPdfDocumentReader(resource, config);
            return pdfReader.read();
        } catch (IllegalArgumentException e) {
            // 如果PDF没有目录结构，降级使用页面PDF文档读取器
            if (e.getMessage() != null && e.getMessage().contains("Document outline")) {
                log.warn("PDF文件 [{}] 没有目录结构，使用 PagePdfDocumentReader 读取", file.getName());
                PagePdfDocumentReader pagePdfReader = new PagePdfDocumentReader(resource, config);
                return pagePdfReader.read();
            }
            throw e;
        }
    }

    /**
     * 读取 JSON 文件
     */
    private List<Document> readJson(Resource resource) {
        JsonReader jsonReader = new JsonReader(resource);
        return jsonReader.get();
    }

    /**
     * 读取文本文件
     */
    private List<Document> readText(Resource resource) {
        TextReader textReader = new TextReader(resource);
        return textReader.get();
    }

    /**
     * 读取 Markdown 文件
     */
    private List<Document> readMarkdown(Resource resource, File file) {
        // 读取配置
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                // 水平线分割生成新文档
                .withHorizontalRuleCreateDocument(true)
                // 不包含代码块
                .withIncludeCodeBlock(false)
                // 不包含引用
                .withIncludeBlockquote(false)
                // 添加文件名元数据
                .withAdditionalMetadata("filename", file.getName())
                .build();
        return new MarkdownDocumentReader(resource, config).get();
    }

    /**
     * 读取 HTML 文件
     */
    private List<Document> readHtml(Resource resource, File file) {
        // 读取配置
        JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.builder()
                // 只提取p标签段落
                .selector("p")
                // 文件编码
                .charset("UTF-8")
                // 包含超链接
                .includeLinkUrls(true)
                // 提取meta标签的元数据
                .metadataTags(List.of("author", "date"))
                // 添加自定义元数据
                .additionalMetadata("filename", file.getName())
                .build();
        return new JsoupDocumentReader(resource, config).get();
    }

    /**
     * 根据网站地址读取 HTML 内容
     *
     * @param url 网站地址
     * @return 文档列表
     * @throws IOException IO异常
     */
    public List<Document> readHtmlFromUrl(String url) throws IOException {
        log.info("开始从URL读取HTML内容: {}", url);

        // 创建URL资源
        Resource resource = new UrlResource(new URL(url));

        // 读取配置
        JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.builder()
                // 只提取p标签段落
                .selector("p")
                // 文件编码
                .charset("UTF-8")
                // 包含超链接
                .includeLinkUrls(true)
                // 提取meta标签的元数据
                .metadataTags(List.of("author", "date", "title", "description"))
                // 添加自定义元数据
                .additionalMetadata("source_url", url)
                .build();

        List<Document> documents = new JsoupDocumentReader(resource, config).get();
        log.info("从URL读取完成，共获取 {} 个文档", documents.size());

        return documents;
    }

    /**
     * 读取 Word/PPT 文件（使用 Tika）
     * Tika 支持多种 Office 文档格式：.doc, .docx, .ppt, .pptx, .xls, .xlsx 等
     */
    private List<Document> readTika(Resource resource) {
        return new TikaDocumentReader(resource).get();
    }
}
