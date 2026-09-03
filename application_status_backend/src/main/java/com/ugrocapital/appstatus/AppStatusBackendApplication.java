package com.ugrocapital.appstatus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Run: mvn spring-boot:run   (or) java -jar target/application-status-backend-1.0.0.jar
 * Then POST to /chat with {"session_id": "session-prachi", "message": "..."}.
 *
 * Equivalent of the Python project's main.py — see ChatController for the
 * actual endpoints (this class only boots Spring).
 */
@SpringBootApplication
public class AppStatusBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppStatusBackendApplication.class, args);
    }
}
