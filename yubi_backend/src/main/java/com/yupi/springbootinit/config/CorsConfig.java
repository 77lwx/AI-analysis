package com.yupi.springbootinit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域配置
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 覆盖所有请求
        registry.addMapping("/**")
                // 允许发送 Cookie
                .allowCredentials(true)
                // 放行哪些域名（必须用 patterns，否则 * 会和 allowCredentials 冲突）
                .allowedOriginPatterns("*")
                //允许下列方法请求
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                //允许携带请求头
                .allowedHeaders("*")
                //指定哪些响应头可以暴露给客户端，"*" 表示所有响应头都可以暴露。
                // 通常情况下，浏览器不会将某些敏感的响应头暴露给前端。
                .exposedHeaders("*");
    }
}
