package com.santana.carpool.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.url:}")
    private String serverUrl;

    @Bean
    public OpenAPI carpoolOpenAPI() {
        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("Carpool Route Optimization API")
                        .description("REST API for optimizing carpool pickup/dropoff routes using Google Maps. "
                                + "Supports single-trip and weekly planning with fair driver rotation.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Santana")
                                .url("https://github.com/luizhenriquesantana/carpool")));

        if (serverUrl != null && !serverUrl.isEmpty()) {
            openAPI.servers(List.of(new Server().url(serverUrl)));
        }

        return openAPI;
    }
}
