package com.novedadeslz.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class LocalUploadResourceConfig implements WebMvcConfigurer {

    @Value("${app.upload.local-dir:./uploads}")
    private String localUploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(localUploadDir).toAbsolutePath().normalize();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath.toUri().toString());
    }
}
