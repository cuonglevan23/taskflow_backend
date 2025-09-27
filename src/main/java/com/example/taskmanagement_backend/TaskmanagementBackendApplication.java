package com.example.taskmanagement_backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling // ✅ NEW: Enable automatic scheduling for overdue task notifications
public class TaskmanagementBackendApplication {

	public static void main(String[] args) {
		// Load .env file before Spring starts
		try {
			Dotenv dotenv = Dotenv.configure()
					.directory("./")
					.ignoreIfMalformed()
					.ignoreIfMissing()
					.load();
			
			// Set environment variables for Spring to pick up
			dotenv.entries().forEach(entry -> {
				System.setProperty(entry.getKey(), entry.getValue());
			});
			
			System.out.println("✅ Dotenv loaded successfully with " + dotenv.entries().size() + " variables");
			
		} catch (Exception e) {
			System.out.println("⚠️ Could not load .env file: " + e.getMessage());
		}
		
		SpringApplication.run(TaskmanagementBackendApplication.class, args);
	}

}
