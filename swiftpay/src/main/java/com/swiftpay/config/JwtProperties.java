package com.swiftpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "swiftpay.jwt")
public record JwtProperties(String secret, long expirationSeconds) {
}
