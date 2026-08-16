package fr.tlprice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TlpriceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TlpriceApplication.class, args);
    }
}
