package com.agent.financial_advisor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.agent.financial_advisor")
public class FinancialAdvisorApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinancialAdvisorApplication.class, args);
	}

}
