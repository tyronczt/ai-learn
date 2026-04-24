package cn.tyron.llm.embedding.milvus;

import cn.tyron.llm.embedding.Chunk;
import cn.tyron.llm.embedding.EmbeddingSearchService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Milvus 向量数据库操作核心服务
 * <p>
 * 封装 Collection 管理、向量插入、向量检索、混合检索等全部操作。
 *
 * @author tyron
 * @create 2026-04-23
 */
@Service
public class MilvusVectorService {

    private static final Logger log = LoggerFactory.getLogger(MilvusVectorService.class);

    private static final String FIELD_ID = "id";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_VECTOR = "vector";

    private final MilvusClientV2 milvusClient;
    private final EmbeddingSearchService embeddingSearchService;
    private final Gson gson = new Gson();

    @Value("${milvus.collection-name}")
    private String collectionName;

    @Value("${milvus.vector-dimension}")
    private int vectorDimension;

    @Value("${milvus.index.params.m}")
    private int hnswM;

    @Value("${milvus.index.params.ef-construction}")
    private int hnswEfConstruction;

    @Value("${milvus.search.default-top-k}")
    private int defaultTopK;

    @Value("${milvus.search.params.ef}")
    private int searchEf;

    public MilvusVectorService(MilvusClientV2 milvusClient, EmbeddingSearchService embeddingSearchService) {
        this.milvusClient = milvusClient;
        this.embeddingSearchService = embeddingSearchService;
    }

    // ===================== Collection 管理 =====================

    /**
     * 创建 Collection（含自定义 Schema + HNSW 索引）
     */
    public void createCollection() {
        if (hasCollection()) {
            log.info("Collection '{}' 已存在，跳过创建", collectionName);
            return;
        }

        // 1. 构建 Schema
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();

        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_ID)
                .dataType(DataType.Int64)
                .isPrimaryKey(true)
                .autoID(true)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_CONTENT)
                .dataType(DataType.VarChar)
                .maxLength(4096)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_SOURCE)
                .dataType(DataType.VarChar)
                .maxLength(256)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_VECTOR)
                .dataType(DataType.FloatVector)
                .dimension(vectorDimension)
                .build());

        // 2. 构建 HNSW 索引
        List<IndexParam> indexParams = List.of(
                IndexParam.builder()
                        .fieldName(FIELD_VECTOR)
                        .indexType(IndexParam.IndexType.HNSW)
                        .metricType(IndexParam.MetricType.COSINE)
                        .extraParams(Map.of("M", hnswM, "efConstruction", hnswEfConstruction))
                        .build()
        );

        // 3. 创建 Collection
        CreateCollectionReq createReq = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(indexParams)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .build();

        try {
            milvusClient.createCollection(createReq);
            log.info("Collection '{}' 创建成功 (dim={}, index=HNSW, metric=COSINE)", collectionName, vectorDimension);
        } catch (Exception e) {
            throw new RuntimeException("创建 Collection '" + collectionName + "' 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除 Collection
     */
    public void dropCollection() {
        if (!hasCollection()) {
            log.info("Collection '{}' 不存在，无需删除", collectionName);
            return;
        }

        try {
            milvusClient.dropCollection(DropCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
            log.info("Collection '{}' 已删除", collectionName);
        } catch (Exception e) {
            throw new RuntimeException("删除 Collection '" + collectionName + "' 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查 Collection 是否存在
     */
    public boolean hasCollection() {
        try {
            return milvusClient.hasCollection(HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
        } catch (Exception e) {
            log.error("检查 Collection '{}' 是否存在时出错: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Milvus 连接异常，无法检查 Collection 状态: " + e.getMessage(), e);
        }
    }

    /**
     * 获取 Collection 统计信息
     */
    public Map<String, Object> getCollectionStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        boolean exists = hasCollection();
        stats.put("collectionName", collectionName);
        stats.put("exists", exists);

        if (exists) {
            try {
                GetCollectionStatsResp statsResp = milvusClient.getCollectionStats(
                        GetCollectionStatsReq.builder()
                                .collectionName(collectionName)
                                .build());
                stats.put("rowCount", statsResp.getNumOfEntities());
            } catch (Exception e) {
                log.error("获取 Collection '{}' 统计信息失败: {}", collectionName, e.getMessage(), e);
                stats.put("rowCount", -1);
                stats.put("error", "获取统计信息失败: " + e.getMessage());
            }
        }

        return stats;
    }

    // ===================== 向量数据操作 =====================

    /**
     * 批量插入 Chunk（先 Embedding 再插入 Milvus）
     *
     * @param chunks 文本块列表
     * @return 插入的行数
     */
    public int insertChunks(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("插入的 Chunk 列表不能为空");
        }

        long totalStart = System.currentTimeMillis();

        // 阶段1：检查/创建 Collection
        long phaseStart = System.currentTimeMillis();
        if (!hasCollection()) {
            createCollection();
        }
        log.info("[耗时统计] Collection 检查/创建: {}ms", System.currentTimeMillis() - phaseStart);

        List<String> texts = chunks.stream()
                .map(Chunk::getContent)
                .collect(Collectors.toList());

        // 阶段2：Embedding 向量化
        phaseStart = System.currentTimeMillis();
        log.info("开始向量化 {} 个文本块...", texts.size());
        List<double[]> embeddings;
        try {
            embeddings = embeddingSearchService.embedBatch(texts, 20, 3);
        } catch (Exception e) {
            throw new RuntimeException("Embedding 向量化失败: " + e.getMessage(), e);
        }
        long embeddingCost = System.currentTimeMillis() - phaseStart;
        log.info("向量化完成, 向量数量={}, 维度={}, 耗时={}ms", embeddings.size(), embeddings.get(0).length, embeddingCost);

        // 阶段3：构建数据行
        phaseStart = System.currentTimeMillis();
        List<JsonObject> rows = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            float[] vector = toFloatArray(embeddings.get(i));

            JsonObject row = new JsonObject();
            row.addProperty(FIELD_CONTENT, chunk.getContent());
            row.addProperty(FIELD_SOURCE, chunk.getSource() != null ? chunk.getSource() : "unknown");
            row.add(FIELD_VECTOR, gson.toJsonTree(vector));
            rows.add(row);
        }
        log.debug("[耗时统计] 数据行构建: {}ms", System.currentTimeMillis() - phaseStart);

        // 阶段4：Milvus 插入
        phaseStart = System.currentTimeMillis();
        try {
            InsertResp insertResp = milvusClient.insert(InsertReq.builder()
                    .collectionName(collectionName)
                    .data(rows)
                    .build());
            int insertCount = Math.toIntExact(insertResp.getInsertCnt());
            long insertCost = System.currentTimeMillis() - phaseStart;
            long totalCost = System.currentTimeMillis() - totalStart;
            log.info("向量数据插入成功, 插入行数={}, Milvus插入耗时={}ms, 总耗时={}ms", insertCount, insertCost, totalCost);
            return insertCount;
        } catch (Exception e) {
            long insertCost = System.currentTimeMillis() - phaseStart;
            log.error("Milvus 插入失败, 已耗时={}ms, error={}", insertCost, e.getMessage());
            throw new RuntimeException("Milvus 插入失败: " + e.getMessage(), e);
        }
    }

    // ===================== 向量检索 =====================

    /**
     * 混合检索（向量 + 元数据过滤）
     *
     * @param request 包含查询文本、topK、过滤条件的请求
     * @return 检索结果列表
     */
    public List<MilvusSearchResult> hybridSearch(MilvusSearchRequest request) {
        String filter = buildFilterExpression(request);
        int topK = request.getTopK() > 0 ? request.getTopK() : defaultTopK;
        return doSearch(request.getQuery(), topK, filter);
    }

    /**
     * 纯标量条件查询（不使用向量）
     *
     * @param filterExpression Milvus 过滤表达式
     * @param limit            返回数量上限
     * @return 查询结果列表
     */
    public List<MilvusSearchResult> queryByFilter(String filterExpression, int limit) {
        if (filterExpression == null || filterExpression.isBlank()) {
            throw new IllegalArgumentException("过滤表达式不能为空");
        }
        if (!hasCollection()) {
            throw new IllegalStateException("Collection '" + collectionName + "' 不存在，请先创建");
        }

        try {
            QueryResp queryResp = milvusClient.query(QueryReq.builder()
                    .collectionName(collectionName)
                    .filter(filterExpression)
                    .outputFields(List.of(FIELD_CONTENT, FIELD_SOURCE))
                    .limit(limit > 0 ? limit : defaultTopK)
                    .consistencyLevel(ConsistencyLevel.STRONG)
                    .build());

            return queryResp.getQueryResults().stream()
                    .map(result -> {
                        Map<String, Object> entity = result.getEntity();
                        return MilvusSearchResult.builder()
                                .id(toLong(entity.get(FIELD_ID)))
                                .content(toString(entity.get(FIELD_CONTENT)))
                                .source(toString(entity.get(FIELD_SOURCE)))
                                .score(0f)
                                .distance(0f)
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Milvus 标量查询失败 (filter='" + filterExpression + "'): " + e.getMessage(), e);
        }
    }

    // ===================== 示例数据 =====================

    /**
     * 初始化示例数据（用于演示和测试）
     *
     * @return 插入的行数
     */
    public int initSampleData() {
        List<Chunk> sampleChunks = List.of(
                new Chunk("1", "七天无理由退货政策：商品在签收后7天内，如不满意可申请退货", "退货政策"),
                new Chunk("2", "生鲜类商品不支持七天无理由退货，因其属于鲜活易腐类商品", "生鲜退货政策"),
                new Chunk("3", "退货流程：进入我的订单 -> 申请退货 -> 填写退货原因 -> 等待审核", "退货流程"),
                new Chunk("4", "退款将在退货成功后1-3个工作日内退回原支付账户", "退款时效"),
                new Chunk("5", "质量问题退货：需提供照片证明，运费由卖家承担", "质量问题退货"),
                new Chunk("6", "会员积分规则：每消费1元累积1积分，100积分可抵扣1元", "会员积分"),
                new Chunk("7", "配送时效：普通快递3-5个工作日，加急快递1-2个工作日", "配送时效"),
                new Chunk("8", "客服工作时间：周一至周日 9:00-21:00，节假日照常服务", "客服信息"),
                new Chunk("9", "支付方式支持：支付宝、微信支付、银行卡、信用卡、花呗", "支付方式"),
                new Chunk("10", "发票申请：订单完成后30天内可申请电子发票，在我的订单中操作", "发票信息")
        );

        return insertChunks(sampleChunks);
    }

    // ===================== 内部方法 =====================

    /**
     * 执行向量检索的核心方法
     */
    private List<MilvusSearchResult> doSearch(String query, int topK, String filter) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("查询文本不能为空");
        }
        if (!hasCollection()) {
            throw new IllegalStateException("Collection '" + collectionName + "' 不存在，请先创建");
        }

        // 1. 向量化查询文本
        double[] queryEmbedding;
        try {
            queryEmbedding = embeddingSearchService.embed(query);
        } catch (Exception e) {
            throw new RuntimeException("查询文本 Embedding 失败: " + e.getMessage(), e);
        }
        float[] queryVector = toFloatArray(queryEmbedding);

        log.info("执行向量检索, query='{}', topK={}, filter={}", query, topK, filter);

        // 2. 构建检索请求
        SearchReq.SearchReqBuilder builder = SearchReq.builder()
                .collectionName(collectionName)
                .data(List.of(new io.milvus.v2.service.vector.request.data.FloatVec(queryVector)))
                .annsField(FIELD_VECTOR)
                .topK(topK)
                .outputFields(List.of(FIELD_CONTENT, FIELD_SOURCE))
                .searchParams(Map.of("ef", searchEf))
                .consistencyLevel(ConsistencyLevel.STRONG);

        if (filter != null && !filter.isBlank()) {
            builder.filter(filter);
        }

        // 3. 执行检索
        SearchResp searchResp;
        try {
            searchResp = milvusClient.search(builder.build());
        } catch (Exception e) {
            throw new RuntimeException("Milvus 向量检索失败 (query='" + query + "'): " + e.getMessage(), e);
        }

        // 4. 解析结果
        List<MilvusSearchResult> results = new ArrayList<>();
        List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();

        if (searchResults != null && !searchResults.isEmpty()) {
            for (SearchResp.SearchResult result : searchResults.get(0)) {
                Map<String, Object> entity = result.getEntity();
                results.add(MilvusSearchResult.builder()
                        .id(toLong(result.getId()))
                        .content(toString(entity.get(FIELD_CONTENT)))
                        .source(toString(entity.get(FIELD_SOURCE)))
                        .score(result.getScore())
                        .distance(0f)
                        .build());
            }
        }

        log.info("向量检索完成, 结果数量={}", results.size());
        return results;
    }

    /**
     * 构建过滤表达式
     */
    private String buildFilterExpression(MilvusSearchRequest request) {
        if (request == null) {
            return null;
        }

        // 优先使用自定义表达式
        if (request.getFilterExpression() != null && !request.getFilterExpression().isBlank()) {
            return request.getFilterExpression().trim();
        }
        // 使用 sourceFilter 构建简单过滤
        if (request.getSourceFilter() != null && !request.getSourceFilter().isBlank()) {
            return String.format("%s == '%s'", FIELD_SOURCE, escapeMilvusStringLiteral(request.getSourceFilter().trim()));
        }
        return null;
    }

    private String escapeMilvusStringLiteral(String value) {
        return value.replace("\\", "\\\\")
                .replace("'", "\\'");
    }

    /**
     * double[] 转 float[]（Milvus SDK 需要 float[]）
     */
    private float[] toFloatArray(double[] doubleArray) {
        float[] floatArray = new float[doubleArray.length];
        for (int i = 0; i < doubleArray.length; i++) {
            floatArray[i] = (float) doubleArray[i];
        }
        return floatArray;
    }

    /**
     * 安全转 Long
     */
    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全转 String
     */
    private String toString(Object value) {
        return value != null ? value.toString() : null;
    }
}
