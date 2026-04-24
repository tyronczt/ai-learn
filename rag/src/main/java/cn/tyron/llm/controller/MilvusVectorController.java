package cn.tyron.llm.controller;

import cn.tyron.llm.embedding.Chunk;
import cn.tyron.llm.embedding.milvus.MilvusSearchRequest;
import cn.tyron.llm.embedding.milvus.MilvusSearchResult;
import cn.tyron.llm.embedding.milvus.MilvusVectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Milvus 向量数据库 REST API 控制器
 * <p>
 * 提供 Collection 管理、向量插入、向量检索、混合检索等完整操作接口。
 *
 * @author tyron
 * @create 2026-04-23
 */
@Tag(name = "Milvus向量数据库", description = "Milvus 向量数据库完整操作接口（Collection管理、向量插入、检索）")
@RestController
@RequestMapping("/rag/milvus")
@Validated
public class MilvusVectorController {

    private static final Logger log = LoggerFactory.getLogger(MilvusVectorController.class);

    private final MilvusVectorService milvusVectorService;

    public MilvusVectorController(MilvusVectorService milvusVectorService) {
        this.milvusVectorService = milvusVectorService;
    }

    // ===================== Collection 管理 =====================

    @Operation(summary = "创建 Collection", description = "创建 Milvus Collection（含 Schema 和 HNSW 索引），如已存在则跳过")
    @PostMapping("/collection/create")
    public ResponseEntity<Map<String, Object>> createCollection() {
        log.info("收到创建 Collection 请求");
        milvusVectorService.createCollection();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Collection 创建成功（或已存在）"
        ));
    }

    @Operation(summary = "删除 Collection", description = "删除 Milvus Collection 及其所有数据")
    @DeleteMapping("/collection/drop")
    public ResponseEntity<Map<String, Object>> dropCollection() {
        log.info("收到删除 Collection 请求");
        milvusVectorService.dropCollection();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Collection 已删除"
        ));
    }

    @Operation(summary = "Collection 状态", description = "获取 Collection 是否存在、行数等统计信息")
    @GetMapping("/collection/status")
    public ResponseEntity<Map<String, Object>> getCollectionStatus() {
        Map<String, Object> stats = milvusVectorService.getCollectionStats();
        return ResponseEntity.ok(stats);
    }

    // ===================== 向量数据操作 =====================

    @Operation(summary = "插入向量数据", description = "接收 Chunk 列表，自动调用 Embedding API 向量化后插入 Milvus")
    @PostMapping("/vectors/insert")
    public ResponseEntity<Map<String, Object>> insertVectors(
            @RequestBody List<Chunk> chunks) {
        log.info("收到向量插入请求, chunkCount={}", chunks.size());
        int insertCount = milvusVectorService.insertChunks(chunks);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "insertCount", insertCount,
                "message", String.format("成功插入 %d 条向量数据", insertCount)
        ));
    }

    @Operation(summary = "初始化示例数据", description = "插入预设的示例 Chunk 数据（含退货政策、会员积分、配送时效等）")
    @PostMapping("/vectors/init-sample")
    public ResponseEntity<Map<String, Object>> initSampleData() {
        log.info("收到初始化示例数据请求");
        int insertCount = milvusVectorService.initSampleData();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "insertCount", insertCount,
                "message", String.format("示例数据初始化成功，插入 %d 条", insertCount)
        ));
    }

    // ===================== 向量检索 =====================

    @Operation(summary = "向量相似度检索", description = "输入查询文本，执行 ANN 近似最近邻向量检索，可选传入 sourceFilter 或 filterExpression 做元数据过滤")
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestBody MilvusSearchRequest request) {
        log.info("收到向量检索请求, query='{}', topK={}, sourceFilter='{}', filter='{}'",
                request.getQuery(), request.getTopK(), request.getSourceFilter(), request.getFilterExpression());

        int topK = request.getTopK() > 0 ? request.getTopK() : 5;
        List<MilvusSearchResult> results;
        if ((request.getSourceFilter() != null && !request.getSourceFilter().isBlank())
                || (request.getFilterExpression() != null && !request.getFilterExpression().isBlank())) {
            results = milvusVectorService.hybridSearch(new MilvusSearchRequest(
                    request.getQuery(),
                    topK,
                    request.getSourceFilter(),
                    request.getFilterExpression()
            ));
        } else {
            results = milvusVectorService.hybridSearch(new MilvusSearchRequest(
                    request.getQuery(),
                    topK,
                    null,
                    null
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "query", request.getQuery(),
                "topK", topK,
                "sourceFilter", request.getSourceFilter(),
                "filterExpression", request.getFilterExpression(),
                "resultCount", results.size(),
                "results", results
        ));
    }

    @Operation(summary = "标量条件查询", description = "使用 Milvus 过滤表达式进行纯标量查询（不使用向量）")
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> query(
            @Parameter(description = "Milvus 过滤表达式", example = "source == '退货政策'")
            @RequestParam String filter,
            @Parameter(description = "返回数量上限")
            @RequestParam(defaultValue = "10") int limit) {
        log.info("收到标量查询请求, filter='{}', limit={}", filter, limit);

        List<MilvusSearchResult> results = milvusVectorService.queryByFilter(filter, limit);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "filter", filter,
                "resultCount", results.size(),
                "results", results
        ));
    }

    // ===================== 异常处理 =====================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        log.warn("状态错误: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "success", false,
                "error", e.getMessage()
        ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("运行时错误: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
        ));
    }
}
