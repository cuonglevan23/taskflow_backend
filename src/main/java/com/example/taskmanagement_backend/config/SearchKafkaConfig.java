package com.example.taskmanagement_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * Kafka configuration for search indexing events
 * Uses Spring Boot auto-configuration with custom settings
 */
@Configuration
@EnableKafka
public class SearchKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    // Spring Boot will auto-configure KafkaTemplate based on application.properties
    // We just need to ensure JSON deserializer trusts our event packages

    @Bean
    public JsonDeserializer<Object> jsonDeserializer() {
        JsonDeserializer<Object> deserializer = new JsonDeserializer<>();
        deserializer.addTrustedPackages("com.example.taskmanagement_backend.events");
        deserializer.setUseTypeHeaders(false);
        return deserializer;
    }
}
