package dev.ronaldofojr.pdv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PdvApplication {

    public static void main(String[] args) {
        SpringApplication.run(PdvApplication.class, args);
    }
}
