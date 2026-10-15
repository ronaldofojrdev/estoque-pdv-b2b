package dev.ronaldofojr.pdv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Seed seed) {

    public record Jwt(String secret, long expirationMinutes) {
    }

    public record Cors(String allowedOrigins) {
    }

    public record Seed(String adminPassword, String sellerPassword) {
    }
}
