package com.swiftpay.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
    @Bean
    public NewTopic paymentInitiatedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_INITIATED).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic paymentResultTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_RESULT).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic paymentInitiatedDltTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_INITIATED + ".DLT").partitions(1).replicas(1).build();
    }
}
