package cn.tyron.llm;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;

import java.awt.*;

/**
 * @description: 创建一个智能体对象
 * @author: tyron
 * @create: 2026-03-27
 **/
public class QuickStart {
    public static void main(String[] args) {
        // 准备工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SimpleTools());

        // 创建智能体
//        ReActAgent jarvis = ReActAgent.builder()
//                .name("Jarvis")
//                .sysPrompt("你是一个名为 Jarvis 的助手")
//                .model(OpenAIChatModel.builder()
//                                .baseUrl("https://api.minimaxi.com/v1")
//                .apiKey("sk-cp-TnaY0SNRAGgiFqFKQL5VGzfTEQHNFE92j-z2gsMxaWaIGQMRf3GP9_XxNmFvI-JVVe3dbm94-VxYLM7C8oOD3du-iErD75RNMoeOC5uGGQAdinJv6MbKJys")
//                .modelName("MiniMax-M2.7")
//                        .baseUrl("https://coding.dashscope.aliyuncs.com/v1")
//                .apiKey("sk-sp-0bfe3b3113e548e5bbd67aea5e82e718")
//                .modelName("qwen3.5-plus")
//                                .baseUrl("https://coding.dashscope.aliyuncs.com/v1")
//                .apiKey("sk-sp-3faf8233f00541abb638c2db209f9c94")
//                .modelName("qwen3.5-plus")
//                        .build())
//                .toolkit(toolkit)
//                .build();
        ReActAgent jarvis = ReActAgent.builder()
                .name("Jarvis")
                .sysPrompt("你是一个名为 Jarvis 的助手")
                .model(OpenAIChatModel.builder()
                        .baseUrl("https://aihubmix.com/v1")
                        .apiKey("sk-4VXMEKJa8pJytWKJB301392dF88d42Bf8fFb6b91237cF184")
                        .modelName("gpt-4o-free")
                        .build())
                .toolkit(toolkit)
                .build();

        // 发送消息
        Msg msg = Msg.builder()
                .textContent("你好！Jarvis，现在几点了？")
                .build();

        Msg response = jarvis.call(msg).block();
        System.out.println(response.getTextContent());
    }
}
