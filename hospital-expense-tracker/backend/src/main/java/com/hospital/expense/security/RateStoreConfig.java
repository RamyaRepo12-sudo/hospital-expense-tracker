package com.hospital.expense.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class RateStoreConfig {
    /** RATE_LIMIT_STORE=mongo (default, shared by all instances) or memory (one instance, no database write per request). */
    @Bean
    RateStore rateStore(MongoTemplate template, @Value("${app.rate-limit.store}") String kind) {
        return "memory".equalsIgnoreCase(kind) ? new MemoryRateStore() : new MongoRateStore(template);
    }
}
