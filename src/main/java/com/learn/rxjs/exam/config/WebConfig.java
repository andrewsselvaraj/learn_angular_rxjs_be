package com.learn.rxjs.exam.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final NetworkSimulationInterceptor networkSimulation;

    public WebConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins,
                     NetworkSimulationInterceptor networkSimulation) {
        this.allowedOrigins = allowedOrigins;
        this.networkSimulation = networkSimulation;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(networkSimulation)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/chaos/**", "/api/chaos", "/api/admin/**");
    }
}
