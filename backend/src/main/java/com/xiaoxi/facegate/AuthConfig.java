package com.xiaoxi.facegate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthConfig implements WebMvcConfigurer {
    private final RedisState redis;
    public AuthConfig(RedisState redis) { this.redis = redis; }

    static String token(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.length() > 100)
            throw new ApiException(401, "请先登录");
        return header.substring(7);
    }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
                res.setHeader("Cache-Control", "no-store");
                if (redis.session(token(req)) == null) throw new ApiException(401, "登录已过期，请重新登录");
                return true;
            }
        }).addPathPatterns("/api/**").excludePathPatterns("/api/auth/login", "/api/health");
    }
}
