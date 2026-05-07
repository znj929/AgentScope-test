package org.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域配置类
 * 解决前端调用接口时的跨域问题
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许所有域名进行跨域访问
        // 注意：当 allowCredentials 为 true 时，不能使用 "*"，需要明确指定或使用 allowedOriginPatterns
        config.addAllowedOriginPattern("*");
        // 允许跨域的HTTP方法
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");
        // 允许携带认证信息（如cookie）
        config.setAllowCredentials(false);  // 改为 false，避免与 * 冲突
        // 允许跨域的请求头
        config.addAllowedHeader("*");
        // 预检请求的有效期，单位为秒
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有路径生效
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}