package com.vaibhav.ediflow.integration;

import com.vaibhav.ediflow.ingestion.IngestionApplication;
import com.vaibhav.ediflow.order.OrderApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableScheduling
@EntityScan("com.vaibhav.ediflow.order.domain")
@EnableJpaRepositories("com.vaibhav.ediflow.order.repository")
@ComponentScan(
    basePackages = {"com.vaibhav.ediflow.ingestion", "com.vaibhav.ediflow.order"},
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = {IngestionApplication.class, OrderApplication.class}))
public class IntegrationTestApplication { }
