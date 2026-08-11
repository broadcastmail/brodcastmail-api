package com.broadcastmail.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

    @SpringBootApplication(scanBasePackages = {"com.broadcastmail.api", "com.broadcastmail.common"})    @EnableScheduling
    @ConfigurationPropertiesScan
    @EnableJpaRepositories(basePackages = {"com.broadcastmail.api", "com.broadcastmail.common"})
    @EntityScan(basePackages = {"com.broadcastmail.api", "com.broadcastmail.common"})
    public class ApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

}
