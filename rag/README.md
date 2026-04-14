# RAG (Retrieval-Augmented Generation)

> 检索增强生成技术学习与实践笔记

## 📖 项目简介

本项目是 Java 开发者学习 RAG 技术的教程笔记，记录了从理论基础到实践应用的完整学习路径。

**RAG (检索增强生成)** 是一种将强大的**信息检索 (IR)** 技术与**生成式大语言模型 (LLM)** 相结合的框架，让 LLM 学会"开卷考试"，既能利用自己学到的知识，也能随时查阅外部资料。

## 📚 学习笔记

### 一、[什么是 RAG？](docs/一、什么是%20RAG？.md)

1. **核心定义**: RAG 的基本概念和技术原理
2. **技术演进**:
   - 初级 RAG（Naive RAG）: 基础线性流程
   - 高级 RAG（Advanced RAG）: 增加检索前后优化步骤
   - 模块化 RAG（Modular RAG）: 积木式可编排流程
3. **应用场景**: 智能客服、研发助手、医疗助手、法律咨询、教育辅导、企业知识库等
4. **优势与局限**: 从知识管理、工程落地和性能指标三个维度深度分析

### 二、[为什么要使用 RAG？](docs/二、为什么要使用%20RAG？.md)

1. **LLM 的局限性**
2. **技术选型对比**: RAG vs. 微调 vs. 提示工程
3. **实战思考**: RAG 与传统搜索的对比分析

### 三、[如何上手 RAG？](docs/三、如何上手%20RAG？.md)

1. **基础工具链选择**:
2. **MVP 四步构建法**: 数据准备与清洗 → 索引构建 → 检索策略优化 → 生成与提示工程
3. **上手路径**: 低代码快速验证 vs 工程化开发
4. **常见难点与优化**: 文档解析、分块策略、专有名词召回、新旧版本并存等
5. **评估体系**: 上下文精确度、召回率、忠实度、答案相关性

### 四、[使用 LlamaIndex 构建一个简单的RAG系统](docs/四、使用%20LlamaIndex%20构建一个简单的RAG系统.md)

1. **核心功能**: 数据索引构建、高效检索、与 LLM 无缝集成
2. **为什么用**: 开箱即用、灵活性高、社区活跃、与 LangChain 互补
3. **实战演示**: Python + LlamaIndex + 阿里百炼 API 构建 RAG

### 五、[数据加载](docs/五、数据加载.md)

1. **文档加载器**: SpringAI DocumentReader 体系，支持 PDF/Word/TXT/HTML/Markdown/JSON 等
2. **文档清洗**: 去除噪声、统一格式，为分片和向量化提供干净数据
3. **Unstructured 库**: 统一接口处理多格式文档，自动识别标题、段落、表格等结构

### 六、[文本分块](docs/六、文本分块.md)

1. **分块策略**: 固定大小分块、重叠分块、语义分块、递归字符分块
2. **分块参数**: chunk_size、chunk_overlap、 separators
3. **代码实现**: 基于 Spring AI 的分块策略抽象与实现

### 七、[向量嵌入](docs/七、向量嵌入.md)

> 待补充



## 🔗 他山之石

### 优秀教程

- [RAG 技术全栈指南](https://github.com/datawhalechina/all-in-rag)
  - 在线阅读：https://datawhalechina.github.io/all-in-rag/#/
- [RAG 技术教程](https://github.com/vivy-yi/rag-tutorial)
  - 在线阅读：https://vivy-yi.github.io/rag-tutorial/
- [企业级 Agentic RAG 智能体](https://github.com/nageoffer/ragent)
  - 在线阅读：https://nageoffer.com/ragent/
- [Deeptoai 系列 RAG 教程](https://github.com/foreveryh/Awesome-LLM-RAG-tutorial/)
  - 在线阅读：https://rag.deeptoai.com/

### 开源项目

- [ragflow](https://github.com/infiniflow/ragflow) - 企业级 RAG 引擎

## 📝 学习心得

> RAG 不是银弹，但在很多场景下是最优解。关键是理解其适用场景和局限性，做好工程化落地。

## 🤝 贡献

欢迎 Issue 和 PR，共同完善 RAG 学习笔记！

## 📄 许可证

MIT License
