package com.example.template.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.template.interceptor.RequestResponseLoggingInterceptor;
import com.example.template.interceptor.SubscriptionRateLimitInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RequestResponseLoggingInterceptor requestResponseLoggingInterceptor;
    private final SubscriptionRateLimitInterceptor subscriptionRateLimitInterceptor;

    public WebConfig(RequestResponseLoggingInterceptor requestResponseLoggingInterceptor,
                     SubscriptionRateLimitInterceptor subscriptionRateLimitInterceptor) {
        this.requestResponseLoggingInterceptor = requestResponseLoggingInterceptor;
        this.subscriptionRateLimitInterceptor = subscriptionRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Log every request first (correlation id set up before anything else).
        registry.addInterceptor(requestResponseLoggingInterceptor)
                .addPathPatterns("/**");

        // Rate limit authenticated app traffic, but never login, docs, or actuator.
        registry.addInterceptor(subscriptionRateLimitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/actuator/**", "/swagger-ui/**",
                        "/swagger-ui.html", "/v3/api-docs/**", "/api-docs/**");
    }
}
