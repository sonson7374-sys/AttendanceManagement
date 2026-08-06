package com.attendance.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class GenerateHashTest {

    @Test
    void printHash() {
        var encoder = new BCryptPasswordEncoder(10);
        String hash = encoder.encode("Admin1234!");
        System.out.println("=== BCrypt hash for Admin1234! ===");
        System.out.println(hash);
        System.out.println("==================================");
    }
}
