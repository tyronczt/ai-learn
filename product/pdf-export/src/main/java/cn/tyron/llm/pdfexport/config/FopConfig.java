package cn.tyron.llm.pdfexport.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.FopConfParser;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.MimeConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;

@Slf4j
@Component
public class FopConfig {

    @Value("${spring.thymeleaf.prefix}")
    private String templatePrefix;

    private FopFactory fopFactory;

    @PostConstruct
    public void init() throws Exception {
        log.info("========== FOP 配置初始化开始 ==========");
        
        // 创建临时目录
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "fop-config");
        tempDir.mkdirs();
        File fontsDir = new File(tempDir, "fonts");
        fontsDir.mkdirs();
        File confFile = new File(tempDir, "fop.xconf");
        
        log.info("临时配置目录: {}", tempDir.getAbsolutePath());
        log.info("字体目录: {}", fontsDir.getAbsolutePath());
        
        // 复制字体文件（classpath 优先；缺失时在 Windows 下从系统字体目录回退）
        copyFont("fonts/msyh.ttc", fontsDir, "msyh.ttc");
        copyFont("fonts/simsun.ttc", fontsDir, "simsun.ttc");
        copyFont("fonts/simhei.ttf", fontsDir, "simhei.ttf");
        copyFont("fonts/STSong.ttf", fontsDir, "STSong.ttf");
        copyFontFromSystemIfMissing(fontsDir, "msyh.ttc", "C:/Windows/Fonts/msyh.ttc");
        copyFontFromSystemIfMissing(fontsDir, "simsun.ttc", "C:/Windows/Fonts/simsun.ttc");
        copyFontFromSystemIfMissing(fontsDir, "simhei.ttf", "C:/Windows/Fonts/simhei.ttf");
        
        // 生成配置文件 - 字体使用绝对路径
        String fontsAbsPath = fontsDir.getAbsolutePath().replace("\\", "/");
        String confContent = """
            <?xml version="1.0"?>
            <fop version="1.0">
                <base>file:/%1$s</base>
                <source-resolution>72</source-resolution>
                <target-resolution>72</target-resolution>
                <default-page-settings width="21cm" height="29.7cm"/>
                <renderers>
                    <renderer mime="application/pdf">
                        <filterList>
                            <value>flate</value>
                        </filterList>
                        <fonts>
                            <!-- 微软雅黑 -->
                            <font kerning="yes" embed-url="file:/%1$s/msyh.ttc" sub-font="Microsoft YaHei">
                                <font-triplet name="Microsoft YaHei" style="normal" weight="normal"/>
                                <font-triplet name="Microsoft YaHei" style="normal" weight="bold"/>
                                <font-triplet name="Microsoft YaHei" style="normal" weight="400"/>
                                <font-triplet name="Microsoft YaHei" style="normal" weight="700"/>
                                <font-triplet name="微软雅黑" style="normal" weight="normal"/>
                                <font-triplet name="微软雅黑" style="normal" weight="bold"/>
                                <font-triplet name="微软雅黑" style="normal" weight="400"/>
                                <font-triplet name="微软雅黑" style="normal" weight="700"/>
                            </font>
                            <!-- 宋体 -->
                            <font kerning="yes" embed-url="file:/%1$s/simsun.ttc" sub-font="SimSun">
                                <font-triplet name="SimSun" style="normal" weight="normal"/>
                                <font-triplet name="SimSun" style="normal" weight="bold"/>
                                <font-triplet name="SimSun" style="normal" weight="400"/>
                                <font-triplet name="SimSun" style="normal" weight="700"/>
                                <font-triplet name="宋体" style="normal" weight="normal"/>
                                <font-triplet name="宋体" style="normal" weight="bold"/>
                                <font-triplet name="宋体" style="normal" weight="400"/>
                                <font-triplet name="宋体" style="normal" weight="700"/>
                            </font>
                            <!-- 黑体 -->
                            <font kerning="yes" embed-url="file:/%1$s/simhei.ttf">
                                <font-triplet name="SimHei" style="normal" weight="normal"/>
                                <font-triplet name="SimHei" style="normal" weight="bold"/>
                                <font-triplet name="SimHei" style="normal" weight="400"/>
                                <font-triplet name="SimHei" style="normal" weight="700"/>
                                <font-triplet name="黑体" style="normal" weight="normal"/>
                                <font-triplet name="黑体" style="normal" weight="bold"/>
                                <font-triplet name="黑体" style="normal" weight="400"/>
                                <font-triplet name="黑体" style="normal" weight="700"/>
                            </font>
                            <!-- 华文宋体 -->
                            <font kerning="yes" embed-url="file:/%1$s/STSong.ttf">
                                <font-triplet name="STSong" style="normal" weight="normal"/>
                                <font-triplet name="华文宋体" style="normal" weight="normal"/>
                            </font>
                        </fonts>
                    </renderer>
                </renderers>
            </fop>
            """.formatted(fontsAbsPath);
        
        log.info("FOP 配置内容:\n{}", confContent);
        
        try (FileOutputStream fos = new FileOutputStream(confFile)) {
            fos.write(confContent.getBytes());
        }
        log.info("FOP 配置文件已写入: {}", confFile.getAbsolutePath());
        
        // 构建 FopFactory：必须加载 fop.xconf，否则自定义字体不会注册（会回退到 Times，中文缺字）
        URI baseURI = tempDir.toURI();
        log.info("FOP Base URI: {}", baseURI);
        FopConfParser confParser = new FopConfParser(confFile, baseURI);
        this.fopFactory = confParser.getFopFactoryBuilder().build();
        log.info("FopFactory 构建成功");
        
        // 测试 FOP 渲染
        testFopRendering();
        
        log.info("========== FOP 配置初始化完成 ==========");
    }
    
    private void testFopRendering() {
        log.info("开始测试 FOP 渲染...");
        try {
            String testFo = """
                <?xml version="1.0" encoding="UTF-8"?>
                <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
                  <fo:layout-master-set>
                    <fo:simple-page-master master-name="main"
                          page-width="210mm" page-height="297mm"
                          margin-top="20mm" margin-bottom="20mm"
                          margin-left="25mm" margin-right="25mm">
                      <fo:region-body/>
                    </fo:simple-page-master>
                  </fo:layout-master-set>
                  <fo:page-sequence master-reference="main">
                    <fo:flow flow-name="xsl-region-body">
                      <fo:block font-family="Microsoft YaHei" font-size="24pt" font-weight="bold" text-align="center">
                        会议纪要
                      </fo:block>
                      <fo:block font-family="Microsoft YaHei" font-size="12pt" space-before="12pt">
                        时间：2026 年 4 月 2 日
                      </fo:block>
                    </fo:flow>
                  </fo:page-sequence>
                </fo:root>
                """;
            
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
                org.apache.fop.apps.Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, out);
                                
                javax.xml.transform.Source source = new javax.xml.transform.stream.StreamSource(new StringReader(testFo));
                javax.xml.transform.Result result = new javax.xml.transform.sax.SAXResult(fop.getDefaultHandler());
                                
                javax.xml.transform.Transformer transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
                transformer.transform(source, result);
                            
                byte[] pdfBytes = out.toByteArray();
                log.info("FOP 测试渲染成功，PDF 大小：{} bytes", pdfBytes.length);
                            
                // 检查 PDF 头
                if (pdfBytes.length > 5) {
                    String header = new String(pdfBytes, 0, Math.min(8, pdfBytes.length));
                    if (!header.startsWith("%PDF-")) {
                        log.error("生成的不是有效的 PDF！文件头：{}", header);
                    } else {
                        log.info("PDF 文件头正确: {}", header);
                    }
                }
            }
        } catch (Exception e) {
            log.error("FOP 测试渲染失败：{}", e.getMessage(), e);
        }
    }

    private void copyFont(String fontPath, File fontsDir, String targetFileName) {
        Resource resource = new ClassPathResource(fontPath);
        File destFile = new File(fontsDir, targetFileName);
        if (resource.exists()) {
            try {
                if (!destFile.exists() || destFile.length() == 0) {
                    try (InputStream is = resource.getInputStream();
                         FileOutputStream fos = new FileOutputStream(destFile)) {
                        is.transferTo(fos);
                        log.info("字体文件复制成功: {} -> {} (大小: {} bytes)", 
                            fontPath, destFile.getAbsolutePath(), destFile.length());
                    }
                } else {
                    log.info("字体文件已存在: {}", destFile.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("字体文件复制失败: {}, 错误: {}", fontPath, e.getMessage());
            }
        } else {
            log.warn("字体文件不存在，跳过: {}", fontPath);
        }
    }

    /**
     * 当 classpath 中未携带字体文件时，从 Windows 字体目录兜底复制。
     */
    private void copyFontFromSystemIfMissing(File fontsDir, String targetFileName, String systemFontPath) {
        File destFile = new File(fontsDir, targetFileName);
        if (destFile.exists() && destFile.length() > 0) {
            return;
        }

        File systemFile = new File(systemFontPath);
        if (!systemFile.exists() || systemFile.length() == 0) {
            log.warn("系统字体文件不存在，跳过: {}", systemFontPath);
            return;
        }

        try {
            java.nio.file.Files.copy(
                systemFile.toPath(),
                destFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
            log.info("系统字体复制成功: {} -> {}", systemFontPath, destFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("系统字体复制失败: {} -> {}, 错误: {}", systemFontPath, destFile.getAbsolutePath(), e.getMessage());
        }
    }

    public FopFactory getFopFactory() {
        return fopFactory;
    }
}
