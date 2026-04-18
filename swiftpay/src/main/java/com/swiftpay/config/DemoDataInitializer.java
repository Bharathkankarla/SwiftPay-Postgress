package com.swiftpay.config;

import com.swiftpay.dto.CreateUserRequest;
import com.swiftpay.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {
    private final DemoDataProperties demoDataProperties;
    private final UserService userService;

    @Override
    public void run(String... args) {
        if (!demoDataProperties.seedUsers()) {
            return;
        }

        userService.createUserIfMissing(new CreateUserRequest(
                "sender-1", "Sender One", "sender@swiftpay.com", "9000000001", new BigDecimal("500.00"), "USD", "demo123"
        ));
        userService.createUserIfMissing(new CreateUserRequest(
                "receiver-1", "Receiver One", "receiver@swiftpay.com", "9000000002", new BigDecimal("100.00"), "USD", "demo123"
        ));
        userService.createUserIfMissing(new CreateUserRequest(
                "sender-2", "Sender Two", "sender2@swiftpay.com", "9000000003", new BigDecimal("50.00"), "USD", "demo123"
        ));
    }
}
