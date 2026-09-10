package com.ash.resonance;

import com.ash.resonance.auth.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.ash.resonance.storage.StorageProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, StorageProperties.class})
public class ResonanceServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResonanceServerApplication.class, args);
    }
}
