package cn.tyron.llm.pdfexport.service;

import cn.tyron.llm.pdfexport.config.FopConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.springframework.stereotype.Service;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final FopConfig fopConfig;

    private FopFactory getFopFactory() {
        return fopConfig.getFopFactory();
    }

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * 填充.fo模板中的占位符
     *
     * @param foTemplate .fo模板内容
     * @param data 填充数据
     * @return 填充后的.fo内容
     */
    public String fillTemplate(String foTemplate, Map<String, Object> data) {
        String filled = foTemplate;
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            Object value = entry.getValue();
            String replacement = value != null ? value.toString() : "";
            filled = filled.replace(placeholder, escapeXml(replacement));
        }
        
        return filled;
    }

    /**
     * 从.fo模板中提取所有占位符
     *
     * @param foTemplate .fo模板内容
     * @return 占位符名称列表
     */
    public java.util.List<String> extractPlaceholders(String foTemplate) {
        java.util.List<String> placeholders = new java.util.ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(foTemplate);
        
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (!placeholders.contains(placeholder)) {
                placeholders.add(placeholder);
            }
        }
        
        return placeholders;
    }

    /**
     * 使用FOP将.fo模板渲染为PDF
     *
     * @param foContent 填充后的.fo内容
     * @return PDF字节数组
     */
    public byte[] renderToPdf(String foContent) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            FopFactory factory = getFopFactory();
            FOUserAgent foUserAgent = factory.newFOUserAgent();
            
            Fop fop = factory.newFop(MimeConstants.MIME_PDF, foUserAgent, outputStream);
            
            // 记录 FO 内容
            log.info("FO 内容长度：{} 字符", foContent.length());
            log.debug("FO 内容预览：{}", foContent.substring(0, Math.min(500, foContent.length())));
            
            // 直接使用 FOP 进行转换
            javax.xml.transform.Source source = new javax.xml.transform.stream.StreamSource(new StringReader(foContent));
            javax.xml.transform.Result result = new javax.xml.transform.sax.SAXResult(fop.getDefaultHandler());
            
            javax.xml.transform.Transformer transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            transformer.transform(source, result);
            
            byte[] pdfBytes = outputStream.toByteArray();
            log.info("PDF 渲染成功，大小：{} bytes", pdfBytes.length);
            
            // 验证 PDF 有效性
            if (pdfBytes.length < 1000) {
                String content = new String(pdfBytes);
                log.warn("PDF 文件过小 ({})，可能存在渲染问题。实际内容:\n{}", pdfBytes.length, content);
            }
            
            // 检查是否是有效的 PDF（应该以 %PDF- 开头）
            if (pdfBytes.length > 5) {
                String header = new String(pdfBytes, 0, Math.min(8, pdfBytes.length));
                if (!header.startsWith("%PDF-")) {
                    log.error("生成的不是有效的 PDF 文件！文件头：{}", header);
                    throw new RuntimeException("PDF 渲染失败：生成的文件不是有效的 PDF 格式");
                }
            }
                        
            return pdfBytes;
            
        } catch (Exception e) {
            log.error("PDF渲染失败: {}", e.getMessage(), e);
            throw new RuntimeException("PDF渲染失败: " + e.getMessage(), e);
        }
    }

    /**
     * 填充数据并渲染为PDF
     *
     * @param foTemplate .fo模板内容
     * @param data 填充数据
     * @return PDF字节数组
     */
    public byte[] fillAndRender(String foTemplate, Map<String, Object> data) {
        String filledFo = fillTemplate(foTemplate, data);
        return renderToPdf(filledFo);
    }

    /**
     * XML转义
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
