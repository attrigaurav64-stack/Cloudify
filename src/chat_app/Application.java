package chat_app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  Starting Spring Boot Chat UI Application...");
        System.out.println("  Web UI will be available at: http://localhost:8080");
        System.out.println("=================================================");

        SpringApplication.run(Application.class, args);
    }
}
