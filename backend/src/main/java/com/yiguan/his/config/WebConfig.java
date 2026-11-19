package com.yiguan.his.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 前端静态资源托管（单进程部署）。
 *
 * <p>打包时 `frontend/dist` 会被复制到 jar 内的 {@code static/} 目录；
 * 本配置让浏览器刷新任意前端路由（如 /pharmacy/decoction）时都能回落到
 * {@code index.html}，由前端路由接管，而不会 404。</p>
 *
 * <p>约定：以 {@code api/} 开头的请求交给 Controller 处理，不做回落；
 * 带扩展名的静态文件请求找不到时也返回 404，避免把 .js 请求错误地返回成 HTML。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    static class SpaResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            if (resourcePath.startsWith("api/")) {
                return null;
            }
            if (resourcePath.contains(".")) {
                return null;
            }
            Resource index = new ClassPathResource("/static/index.html");
            return index.exists() ? index : null;
        }
    }
}
