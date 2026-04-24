package cn.tyron.llm.embedding.milvus;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量数据库客户端配置
 * <p>
 * 创建并管理 MilvusClientV2 Spring Bean，应用关闭时自动释放连接。
 *
 * @author tyron
 * @create 2026-04-23
 */
@Configuration
public class MilvusConfig {

    private static final Logger log = LoggerFactory.getLogger(MilvusConfig.class);

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private int port;

    @Value("${milvus.collection-name}")
    private String collectionName;

    @Value("${milvus.vector-dimension}")
    private int vectorDimension;

    private MilvusClientV2 client;

    @Bean
    public MilvusClientV2 milvusClientV2() {
        String uri = String.format("http://%s:%d", host, port);
        log.info("正在连接 Milvus 服务: {}", uri);

        ConnectConfig config = ConnectConfig.builder()
                .uri(uri)
                .connectTimeoutMs(10000)       // 连接超时 10 秒
                .rpcDeadlineMs(30000)          // 单次 RPC 调用超时 30 秒（insert/search 等）
                .keepAliveTimeMs(55000)        // 每 55 秒发送 keepAlive 探测
                .keepAliveTimeoutMs(20000)     // keepAlive 响应超时 20 秒
                .keepAliveWithoutCalls(true)   // 空闲时也发送 keepAlive，防止连接被中间设备断开
                .idleTimeoutMs(300000)         // 空闲 5 分钟后释放连接
                .build();

        client = new MilvusClientV2(config);
        log.info("Milvus 客户端连接成功 (connectTimeout=10s, rpcDeadline=30s, keepAlive=55s)");
        return client;
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            try {
                client.close();
                log.info("Milvus 客户端连接已关闭");
            } catch (Exception e) {
                log.warn("关闭 Milvus 客户端时发生异常: {}", e.getMessage());
            }
        }
    }

    public String getCollectionName() {
        return collectionName;
    }

    public int getVectorDimension() {
        return vectorDimension;
    }
}
