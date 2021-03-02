package ru.smsoft.efp.example.springbootwso2token;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(value = SpringBootWso2TokenConfiguration.class)
public class SpringBootWso2TokenApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringBootWso2TokenApplication.class, args);
	}

}
