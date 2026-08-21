package com.sosmoch.aws_s3_api.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AWS S3 API")
                        .version("1.0.0")
                        .description("Production-ready REST API endpoints handling multi-part file uploads, secure binary stream downloads, structural list tracking, and deletion protocols on Amazon S3 buckets.")
                        .contact(new Contact()
                                .name("Sosmoch espresso maker")
                                .email("s.mchana@heomi.fr")

                        )
                );
    }
}
