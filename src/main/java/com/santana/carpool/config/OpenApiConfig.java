package com.santana.carpool.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
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
                                + "Supports single-trip and weekly planning with fair driver rotation. "
                                + "Authentication supports JWT tokens and OAuth2 (Google and GitHub).")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Santana")
                                .url("https://github.com/luizhenriquesantana/carpool")))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token authentication for local users"))
                        .addSecuritySchemes("oauth2-google", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new io.swagger.v3.oas.models.security.OAuthFlows()
                                        .authorizationCode(new io.swagger.v3.oas.models.security.OAuthFlow()
                                                .authorizationUrl("https://accounts.google.com/o/oauth2/v2/auth")
                                                .tokenUrl("https://oauth2.googleapis.com/token")
                                                .scopes(new io.swagger.v3.oas.models.security.Scopes()
                                                        .addString("profile", "User profile")
                                                        .addString("email", "User email")))))
                        .addSecuritySchemes("oauth2-github", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new io.swagger.v3.oas.models.security.OAuthFlows()
                                        .authorizationCode(new io.swagger.v3.oas.models.security.OAuthFlow()
                                                .authorizationUrl("https://github.com/login/oauth/authorize")
                                                .tokenUrl("https://github.com/login/oauth/access_token")
                                                .scopes(new io.swagger.v3.oas.models.security.Scopes()
                                                        .addString("user:email", "User email"))))));

        if (serverUrl != null && !serverUrl.isEmpty()) {
            openAPI.servers(List.of(new Server().url(serverUrl)));
        }

        return openAPI;
    }
}
