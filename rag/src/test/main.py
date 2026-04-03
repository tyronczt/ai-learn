from llama_index.core import VectorStoreIndex, SimpleDirectoryReader
from llama_index.embeddings.dashscope import DashScopeEmbedding
from llama_index.llms.dashscope import DashScope
import os

DASHSCOPE_API_KEY= "sk-sp-175c47ca77ea44ecbdf15125aecda2f2"

# 1. 加载文档（假设当前目录下有 data/ 文件夹，内含 .txt 或 .pdf 文件）
documents = SimpleDirectoryReader("data").load_data()

# 2. 设置嵌入模型（用于生成向量）
embed_model = DashScopeEmbedding(
    model_name="text-embedding-v2",  # 可选: "text-embedding-v1" 或 "text-embedding-v2"
    api_key=DASHSCOPE_API_KEY
)

# 3. 设置 LLM
llm = DashScope(model_name="qwen-max", temperature=0.1,api_key=DASHSCOPE_API_KEY)

# 4. 构建索引
index = VectorStoreIndex.from_documents(documents, embed_model=embed_model)

# 5. 创建查询引擎
query_engine = index.as_query_engine(llm=llm)

# 6. 提问
response = query_engine.query("请总结这份文档的主要内容。")
print(response)