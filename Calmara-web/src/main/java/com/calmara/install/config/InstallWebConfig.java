package com.calmara.install.config;

import com.calmara.install.interceptor.InstallInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InstallWebConfig implements WebMvcConfigurer {

    private final InstallInterceptor installInterceptor;

    public InstallWebConfig(InstallInterceptor installInterceptor) {
        this.installInterceptor = installInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(installInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/install/**",
                        "/install.html",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.ico",
                        "/error"
                )
                .order(Integer.MIN_VALUE + 1);
    }
}
