package com.yupi.springbootinit.manager;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.Arrays;


/**
 * 用于对接 AI 平台
 */
@Service
@Slf4j
public class AiManager {

    @Resource
    private GenerationParam qianWenClient;

    /**
     * AI 对话
     *
     * @param message
     * @return
     */
    public String doChat(long modelId,String message) {

        final String systemPrompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
                "分析需求：\n" +
                "{数据分析的需求或者目标}\n" +
                "原始数据：\n" +
                "{csv格式的原始数据，用,作为分隔符}\n" +
                "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
                "【【【【【\n" +
                "{前端 Echarts V5 的 option 配置对象js代码（输出 json 格式），合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
                "【【【【【\n" +
                "{明确的数据分析结论、越详细越好，不要生成多余的注释}\n" +
                "【【【【【";

        Generation gen = new Generation();
        com.alibaba.dashscope.common.Message systemMsg = com.alibaba.dashscope.common.Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(systemPrompt)
                .build();
        com.alibaba.dashscope.common.Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(message)
                .build();

        qianWenClient.setMessages(Arrays.asList(systemMsg, userMsg));

        String contents = "";
        try {
            GenerationResult result = gen.call(qianWenClient);
            // 使用 JsonParser 解析 JSON 字符串
            JsonObject jsonObject = JsonParser.parseString(JsonUtils.toJson(result)).getAsJsonObject();
            // 提取 output 对象
            JsonObject output = jsonObject.getAsJsonObject("output");
            // 提取 choices 数组
            JsonArray choices = output.getAsJsonArray("choices");
            // 获取第一个元素的 message 对象
            JsonObject messages = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            // 提取 content 字段
            contents = messages.get("content").getAsString();
        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            // 使用日志框架记录异常信息
            System.err.println("An error occurred while calling the generation service: " + e.getMessage());
        }
        return contents;
    }
}
