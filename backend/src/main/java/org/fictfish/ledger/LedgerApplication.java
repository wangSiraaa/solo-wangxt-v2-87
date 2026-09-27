package org.fictfish.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Fictional fisheries quota ledger workbench backend.
 * Demo only: invented species and permit rules; not connected to any
 * regulatory system and not a real fishing permit.
 */
@SpringBootApplication
public class LedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(LedgerApplication.class, args);
    }
}
