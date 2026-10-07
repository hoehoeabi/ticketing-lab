package com.ticketing.ticketing_lab.global.config;

import com.ticketing.ticketing_lab.domain.queue.interceptor.QueueActiveCheckInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final QueueActiveCheckInterceptor queueActiveCheckInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(queueActiveCheckInterceptor)
                .addPathPatterns("/api/v1/orders/{ticketId}");
    }
}
