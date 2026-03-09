package g11.tusuploaderservice.conf

import g11.tusuploaderservice.interceptors.HeadersInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val headersInterceptor: HeadersInterceptor
) : WebMvcConfigurer{

    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/**") // pattern degli endpoint
                    .allowedOrigins("*") // oppure "*" per tutte
                    .allowedMethods( "HEAD","POST", "PATCH", "DELETE", "OPTIONS", "GET")
                    .allowedHeaders("*")
                    .exposedHeaders("Location", "Tus-Resumable", "Upload-Offset", "Upload-Length", "Upload-Metadata", "Upload-Defer-Length", "Upload-Expires", "Content-Disposition")
            }
        }
    }

    override fun addInterceptors(registry: InterceptorRegistry){
        registry.addInterceptor(headersInterceptor)
            // Apply to all paths
            .addPathPatterns("/**")
            //.excludePathPatterns() for excluding some path from check
    }
}