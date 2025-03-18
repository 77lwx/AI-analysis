package com.yupi.springbootinit.config;


import com.alibaba.dashscope.aigc.generation.GenerationParam;
import lombok.Data;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@Data
public class QianWenConfig {
    @Bean
    public GenerationParam qianWenClient(){
        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey("sk-3056831d19b24af8b0ef09c033bdc84e")
                .model("qwen-plus")
//                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        return param;
    }

 }
