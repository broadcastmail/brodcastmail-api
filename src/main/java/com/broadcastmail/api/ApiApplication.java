package com.broadcastmail.api;

import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.config.ResendProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

    @SpringBootApplication
    @EnableScheduling
    @EnableConfigurationProperties({EncryptionProperties.class, ResendProperties.class})
    @EnableJpaRepositories(basePackages = {"com.broadcastmail.api", "com.broadcastmail.common"})
    @EntityScan(basePackages = {"com.broadcastmail.api", "com.broadcastmail.common"})
    public class ApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

}
