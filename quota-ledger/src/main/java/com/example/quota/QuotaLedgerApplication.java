package com.example.quota;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Fictional fishery co-op quota workbench backend.
 * Demo only: invented species and permit rules; not connected to any
 * regulatory system and not a real fishing permit.
 */
@SpringBootApplication
public class QuotaLedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuotaLedgerApplication.class, args);
    }
}
