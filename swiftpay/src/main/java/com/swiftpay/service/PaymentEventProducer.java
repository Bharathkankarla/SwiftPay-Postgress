package com.swiftpay.service;

import com.swiftpay.config.KafkaTopics;
import com.swiftpay.event.PaymentInitiatedEvent;
import com.swiftpay.event.PaymentResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishInitiated(PaymentInitiatedEvent event) {
        kafkaTemplate.send(KafkaTopics.PAYMENT_INITIATED, event.getTransactionId(), event);
    }

    public void publishResult(PaymentResultEvent event) {
        kafkaTemplate.send(KafkaTopics.PAYMENT_RESULT, event.getTransactionId(), event);
    }
}
