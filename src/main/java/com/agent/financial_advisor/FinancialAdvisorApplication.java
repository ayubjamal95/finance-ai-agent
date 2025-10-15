package com.agent.financial_advisor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "com.agent.financial_advisor")
public class FinancialAdvisorApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinancialAdvisorApplication.class, args);
	}

}
