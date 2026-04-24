一、简介
Milvus 是一个开源的、专为大规模向量相似性搜索和分析而设计的向量数据库。它诞生于 Zilliz 公司，并已成为 LF AI & Data 基金会的顶级项目，在AI领域拥有广泛的应用。

与 FAISS、ChromaDB 等轻量级本地存储方案不同，Milvus 从设计之初就瞄准了生产环境。其采用云原生架构，具备高可用、高性能、易扩展的特性，能够处理十亿、百亿甚至更大规模的向量数据。

官网地址: https://milvus.io/

GitHub: https://github.com/milvus-io/milvus

二、 部署安装
Milvus 提供了多种部署方式，这里以 Milvus Standalone (单机版) 为例。

1. 环境准备
   安装 Docker 与 Docker Compose: 确保系统中已安装并正在运行 Docker 和 Docker Compose。
2. 下载并启动 Milvus

Milvus Standalone 是单机版，适合开发和中小规模场景。它依赖两个外部组件：一个对象存储（用来存索引文件和日志）和一个 etcd（用来存元数据）。

本系列使用 RustFS 替代默认的 MinIO 作为对象存储，另外加了一个 Attu（Milvus 的可视化管理界面），方便你直观地看到数据。

> 如果你已经有运行中的 Milvus 实例，可以跳过这一步，直接看后面的代码部分。

把下面的内容保存为 `docker-compose.yml`，然后执行 `docker compose up -d` 即可启动：

```yaml
name: milvus-stack

services:
  rustfs:
    container_name: rustfs
    image: rustfs/rustfs:1.0.0-alpha.72
    command:
      - "--address"
      - ":9000"
      - "--console-enable"
      - "--access-key"
      - "rustfsadmin"
      - "--secret-key"
      - "rustfsadmin"
      - "/data"
    environment:
      - RUSTFS_ACCESS_KEY=rustfsadmin
      - RUSTFS_SECRET_KEY=rustfsadmin
      - RUSTFS_CONSOLE_ENABLE=true
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - rustfs-data:/data
    healthcheck:
      test: ["CMD", "sh", "-c", "wget -qO- http://localhost:9000/ || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5

  etcd:
    container_name: etcd
    image: quay.io/coreos/etcd:v3.5.18
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
      - ETCD_SNAPSHOT_COUNT=50000
    command: >
      etcd
      -advertise-client-urls=http://etcd:2379
      -listen-client-urls http://0.0.0.0:2379
      --data-dir /etcd
    volumes:
      - etcd-data:/etcd
    healthcheck:
      test: ["CMD", "etcdctl", "endpoint", "health"]
      interval: 30s
      timeout: 20s
      retries: 3

  standalone:
    container_name: milvus-standalone
    image: milvusdb/milvus:v2.6.6
    command: ["milvus", "run", "standalone"]
    security_opt:
      - seccomp:unconfined
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: rustfs:9000
      MINIO_ACCESS_KEY_ID: rustfsadmin
      MINIO_SECRET_ACCESS_KEY: rustfsadmin
    volumes:
      - milvus-data:/var/lib/milvus
    ports:
      - "19530:19530"
      - "9091:9091"
    depends_on:
      - etcd
      - rustfs
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9091/healthz"]
      interval: 30s
      start_period: 90s
      timeout: 20s
      retries: 3

  attu:
    container_name: milvus-attu
    image: zilliz/attu:v2.6.3
    environment:
      MILVUS_URL: milvus-standalone:19530
    ports:
      - "8000:3000"
    depends_on:
      - standalone

volumes:
  rustfs-data:
  etcd-data:
  milvus-data:

networks:
  default:
    name: milvus-net
```

各组件的作用：


| 组件       | 作用                                   | 端口                            |
| ---------- | -------------------------------------- | ------------------------------- |
| rustfs     | 对象存储，存储 Milvus 的索引文件和日志 | 9000（API）、9001（控制台）     |
| etcd       | 元数据存储，管理 Milvus 的集群元信息   | 2379                            |
| standalone | Milvus 单机版服务                      | 19530（gRPC）、9091（健康检查） |
| attu       | Milvus 可视化管理界面                  | 8000                            |

启动后，访问 `http://localhost:8000` 可以打开 Attu 管理界面，直观地查看 Collection、数据和索引。



Milvus核心组件
3.1 Collection (集合)
可以用一个图书馆的比喻来理解 Collection：

Collection (集合): 相当于一个图书馆，是所有数据的顶层容器。一个 Collection 可以包含多个 Partition，每个 Partition 可以包含多个 Entity。
Partition (分区): 相当于图书馆里的不同区域（如“小说区”、“科技区”），将数据物理隔离，让检索更高效。
Schema (模式): 相当于图书馆的图书卡片规则，定义了每本书（数据）必须登记哪些信息（字段）。
Entity (实体): 相当于一本具体的书，是数据本身。
Alias (别名): 相当于一个动态的推荐书单（如“本周精选”），它可以指向某个具体的 Collection，方便应用层调用，实现数据更新时的无缝切换。
Collection 是 Milvus 中最基本的数据组织单位，类似于关系型数据库中的一张**表 (Table)**。是我们存储、管理和查询向量及相关元数据的容器。所有的数据操作，如插入、删除、查询等，都是围绕 Collection 展开的。

一个 Collection 由其 Schema 定义，并包含以下重要的子概念和特性：

3.1.1 Schema
在创建 Collection 之前，必须先定义它的 Schema。 Schema 规定了 Collection 的数据结构，定义了其中包含的所有字段 (Field) 及其属性。一个设计良好的 Schema 是能够保证数据一致性并提升查询性能。

Schema 通常包含以下几类字段：

主键字段 (Primary Key Field): 每个 Collection 必须有且仅有一个主键字段，用于唯一标识每一条数据（实体）。它的值必须是唯一的，通常是整数或字符串类型。
向量字段 (Vector Field): 用于存储核心的向量数据。一个 Collection 可以有一个或多个向量字段，以满足多模态等复杂场景的需求。
标量字段 (Scalar Field): 用于存储除向量之外的元数据，如字符串、数字、布尔值、JSON 等。这些字段可以用于过滤查询，实现更精确的检索。
Schema 设计剖析
![新闻文章示例](https://datawhalechina.github.io/all-in-rag/chapter3/images/3_4_1.webp)
上图以一篇新闻文章为例，展示了一个典型的多模态、混合向量 Schema 设计。它将一篇文章拆解为：唯一的 Article (ID)、文本元数据（如 Title、Author Info）、图像信息（Image URL），并为图像和摘要内容分别生成了密集向量（Image Embedding, Summary Embedding）和稀疏向量（Summary Sparse Embedding）。

3.1.2 Partition (分区)
Partition 是 Collection 内部的一个逻辑划分。每个 Collection 在创建时都会有一个名为 _default 的默认分区。我们可以根据业务需求创建更多的分区，将数据按特定规则（如类别、日期等）存入不同分区。

为什么使用分区？

提升查询性能: 在查询时，可以指定只在一个或几个分区内进行搜索，从而大幅减少需要扫描的数据量，显著提升检索速度。
数据管理: 便于对部分数据进行批量操作，如加载/卸载特定分区到内存，或者删除整个分区的数据。
一个 Collection 最多可以有 1024 个分区。合理利用分区是 Milvus 性能优化的重要手段之一。

3.1.3 Alias (别名)
Alias (别名) 是为 Collection 提供的一个“昵称”。通过为一个 Collection 设置别名，我们可以在应用程序中使用这个别名来执行所有操作，而不是直接使用真实的 Collection 名称。

为什么使用别名？

安全地更新数据：想象一下，你需要对一个在线服务的 Collection 进行大规模的数据更新或重建索引。直接在原 Collection 上操作风险很高。正确的做法是：
创建一个新的 Collection (collection_v2) 并导入、索引好所有新数据。
将指向旧 Collection (collection_v1) 的别名（例如 my_app_collection）原子性地切换到新 Collection (collection_v2) 上。
代码解耦：整个切换过程对上层应用完全透明，无需修改任何代码或重启服务，实现了数据的平滑无缝升级。
3.2 索引 (Index)
如果说 Collection 是 Milvus 的骨架，那么索引 (Index) 就是其加速检索的神经系统。从宏观上看，索引本身就是一种为了加速查询而设计的复杂数据结构。对向量数据创建索引后，Milvus 可以极大地提升向量相似性搜索的速度，代价是会占用额外的存储和内存资源。

Milvus 索引结构与工作原理
![Milvus 索引结构与工作原理](https://datawhalechina.github.io/all-in-rag/chapter3/images/3_4_2.webp)
上图清晰地展示了 Milvus 向量索引的内部组件及其工作流程：

数据结构：这是索引的骨架，定义了向量的组织方式（如 HNSW 中的图结构）。
量化(可选)：数据压缩技术，通过降低向量精度来减少内存占用和加速计算。
结果精炼(可选)：在找到初步候选集后，进行更精确的计算以优化最终结果。
Milvus 支持对标量字段和向量字段分别创建索引。

标量字段索引：主要用于加速元数据过滤，常用的有 INVERTED、BITMAP 等。通常使用推荐的索引类型即可。
向量字段索引：这是 Milvus 的核心。选择合适的向量索引是在查询性能、召回率和内存占用之间做出权衡的艺术。
3.2.1 主要向量索引类型
Milvus 提供了多种向量索引算法，以适应不同的应用场景。以下是几种最核心的类型：

FLAT (精确查找)

原理：暴力搜索（Brute-force Search）。它会计算查询向量与集合中所有向量之间的实际距离，返回最精确的结果。
优点：100% 的召回率，结果最准确。
缺点：速度慢，内存占用大，不适合海量数据。
适用场景：对精度要求极高，且数据规模较小（百万级以内）的场景。
IVF 系列 (倒排文件索引)

原理：类似于书籍的目录。它首先通过聚类将所有向量分成多个“桶”(nlist)，查询时，先找到最相似的几个“桶”，然后只在这几个桶内进行精确搜索。IVF_FLAT、IVF_SQ8、IVF_PQ 是其不同变体，主要区别在于是否对桶内向量进行了压缩（量化）。
优点：通过缩小搜索范围，极大地提升了检索速度，是性能和效果之间很好的平衡。
缺点：召回率不是100%，因为相关向量可能被分到了未被搜索的桶中。
适用场景：通用场景，尤其适合需要高吞吐量的大规模数据集。
HNSW (基于图的索引)

原理：构建一个多层的邻近图。查询时从最上层的稀疏图开始，快速定位到目标区域，然后在下层的密集图中进行精确搜索。
优点：检索速度极快，召回率高，尤其擅长处理高维数据和低延迟查询。
缺点：内存占用非常大，构建索引的时间也较长。
适用场景：对查询延迟有严格要求（如实时推荐、在线搜索）的场景。
DiskANN (基于磁盘的索引)

原理：一种为在 SSD 等高速磁盘上运行而优化的图索引。
优点：支持远超内存容量的海量数据集（十亿级甚至更多），同时保持较低的查询延迟。
缺点：相比纯内存索引，延迟稍高。
适用场景：数据规模巨大，无法全部加载到内存的场景。
3.2.2 如何选择索引？
选择索引没有唯一的“最佳答案”，需要根据业务场景在数据规模、内存限制、查询性能和召回率之间进行权衡。

场景	推荐索引	备注
数据可完全载入内存，追求低延迟	HNSW	内存占用较大，但查询性能和召回率都很优秀。
数据可完全载入内存，追求高吞吐	IVF_FLAT / IVF_SQ8	性能和资源消耗的平衡之选。
数据量巨大，无法载入内存	DiskANN	在 SSD 上性能优异，专为海量数据设计。
追求 100% 准确率，数据量不大	FLAT	暴力搜索，确保结果最精确。
在实际应用中，通常需要通过测试来找到最适合自己数据和查询模式的索引类型及其参数。

3.3 检索
3.3.1 基础向量检索 (ANN Search)
拥有了数据容器 (Collection) 和检索引擎 (Index) 后，最后一步就是从海量数据中高效地检索信息。这是 Milvus 的核心功能之一，近似最近邻 (Approximate Nearest Neighbor, ANN) 检索。与需要计算全部数据的暴力检索（Brute-force Search）不同，ANN 检索利用预先构建好的索引，能够极速地从海量数据中找到与查询向量最相似的 Top-K 个结果。这是一种在速度和精度之间取得极致平衡的策略。

主要参数:
anns_field: 指定要在哪个向量字段上进行检索。
data: 传入一个或多个查询向量。
limit (或 top_k): 指定需要返回的最相似结果的数量。
search_params: 指定检索时使用的参数，例如距离计算方式 (metric_type) 和索引相关的查询参数。
3.3.2 增强检索
在基础的 ANN 检索之上，Milvus 提供了多种增强检索功能，以满足更复杂的业务需求。

过滤检索 (Filtered Search)

在实际应用中，我们很少只进行单纯的向量检索。更常见的需求是“在满足特定条件的向量中，查找最相似的结果”，这就是过滤检索。它将向量相似性检索与标量字段过滤结合在一起。

工作原理：先根据提供的过滤表达式 (filter) 筛选出符合条件的实体，然后仅在这个子集内执行 ANN 检索。这极大地提高了查询的精准度。
应用示例：
电商："检索与这件红色连衣裙最相似的商品，但只看价格低于500元且有库存的。"
知识库："查找与‘人工智能’相关的文档，但只从‘技术’分类下、且发布于2023年之后的文章中寻找。"
范围检索 (Range Search)

有时我们关心的不是最相似的 Top-K 个结果，而是“所有与查询向量的相似度在特定范围内的结果”。

工作原理：范围检索允许定义一个距离（或相似度）的阈值范围。Milvus 会返回所有与查询向量的距离落在这个范围内的实体。
应用示例：
人脸识别："查找所有与目标人脸相似度超过 0.9 的人脸"，用于身份验证。
异常检测："查找所有与正常样本向量距离过大的数据点"，用于发现异常。
多向量混合检索 (Hybrid Search)

这是 Milvus 提供的一种极其强大的高级检索模式，它允许在一个请求中同时检索多个向量字段，并将结果智能地融合在一起。

工作原理：

并行检索：应用针对不同的向量字段（如一个用于文本语义的密集向量，一个用于关键词匹配的稀疏向量，一个用于图像内容的多模态向量）分别发起 ANN 检索请求。
**结果融合 (Rerank)**：Milvus 使用一个重排策略（Reranker）将来自不同检索流的结果合并成一个统一的、更高质量的排序列表。常用的策略有 RRFRanker（平衡各方结果）和 WeightedRanker（可为特定字段结果加权）。
应用示例：

多模态商品检索：用户输入文本“安静舒适的白色耳机”，系统可以同时检索商品的文本描述向量和图片内容向量，返回最匹配的商品。
增强型 RAG: 结合密集向量（捕捉语义）和稀疏向量（精确匹配关键词），实现比单一向量更精准的文档检索效果。
分组检索 (Grouping Search)

分组检索解决了一个常见的痛点：检索结果多样性不足。想象一下，你检索“机器学习”，返回的前10篇文章都来自同一本教科书不同章节。这显然不是理想的结果。

工作原理：分组检索允许指定一个字段（如 document_id）对结果进行分组。Milvus 会在检索后，确保返回的结果中每个组（每个 document_id）只出现一次（或指定的次数），且返回的是该组内与查询最相似的那个实体。
应用示例：
视频检索：检索“可爱的猫咪”，确保返回的视频来自不同的博主。
文档检索：检索“数据库索引”，确保返回的结果来自不同的书籍或来源。
通过这些灵活的检索功能组合，开发者可以构建出满足各种复杂业务需求的向量检索应用。