package com.swiftpay;

import com.swiftpay.config.DemoDataProperties;
import com.swiftpay.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties({DemoDataProperties.class, JwtProperties.class})
public class SwiftpayApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(SwiftpayApplication.class, args);
	}

}
