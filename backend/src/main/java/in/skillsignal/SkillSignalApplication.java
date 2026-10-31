package in.skillsignal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SkillSignalApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillSignalApplication.class, args);
    }
}
