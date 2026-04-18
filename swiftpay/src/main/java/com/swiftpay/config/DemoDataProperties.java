package com.swiftpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "swiftpay.demo")
public record DemoDataProperties(boolean seedUsers) {
}
