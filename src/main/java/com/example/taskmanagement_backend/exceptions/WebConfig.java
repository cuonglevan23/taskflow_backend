package com.example.taskmanagement_backend.exceptions;

import com.example.taskmanagement_backend.config.OnlineStatusInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final OnlineStatusInterceptor onlineStatusInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(onlineStatusInterceptor)
                .addPathPatterns("/api/**") // Chỉ áp dụng cho API endpoints
                .excludePathPatterns(
                    "/api/auth/login",     // Không cần update lastSeen khi login
                    "/api/auth/logout",    // Không cần update lastSeen khi logout
                    "/api/auth/refresh"    // Không cần update lastSeen khi refresh token
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Chỉ xử lý static resources cụ thể, KHÔNG can thiệp vào API routes

        // Thêm mapping cho /images/** để serve premium badges
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/static/images/**")
                .addResourceLocations("file:uploads/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/static/assets/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/uploads/files/**")
                .addResourceLocations("file:uploads/")
                .setCachePeriod(3600);

        // Đặt order thấp để không can thiệp vào API mappings
    }
}
