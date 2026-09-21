package com.mortgage.rate_concession_service.config;

import com.mortgage.rate_concession_service.security.AuthenticationInterceptor;
import com.mortgage.rate_concession_service.security.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthenticationInterceptor authenticationInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebMvcConfig(
            AuthenticationInterceptor authenticationInterceptor,
            CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
