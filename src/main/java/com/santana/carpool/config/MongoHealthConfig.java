package com.santana.carpool.config;

import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoHealthConfig {

    @Bean
    public HealthIndicator mongoHealthIndicator(MongoClient mongoClient,
            @Value("${spring.mongodb.database:carpool}") String databaseName) {
        return () -> {
            try {
                Document result = mongoClient.getDatabase(databaseName)
                        .runCommand(new Document("hello", 1));
                return Health.up()
                        .withDetail("database", databaseName)
                        .withDetail("maxWireVersion", result.getInteger("maxWireVersion"))
                        .build();
            } catch (Exception ex) {
                return Health.down(ex).build();
            }
        };
    }
}
