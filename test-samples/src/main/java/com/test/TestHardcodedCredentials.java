package com.test;

/**
 * Test file to verify hardcoded credentials inspection works.
 *
 * This should trigger:
 * - ERROR: Hardcoded credentials detected
 */
public class TestHardcodedCredentials {

    /**
     * BAD: Hardcoded password
     * Should show RED underline on the password string
     */
    public void hardcodedPasswordBad() {
        String dbPassword = "MySecretPassword123!";  // ← Should show ERROR here
        String apiKey = "sk_live_123456789abcdef";    // ← Should show ERROR here
        String token = "ghp_AbCdEfGhIjKlMnOpQrStUvWxYz";  // ← Should show ERROR here
    }

    /**
     * GOOD: Using environment variables or config
     * Should NOT show any errors
     */
    public void hardcodedPasswordGood() {
        String dbPassword = System.getenv("DB_PASSWORD");  // ← Good practice
        String apiKey = System.getProperty("api.key");      // ← Good practice
    }

    /**
     * GOOD: Using placeholders (not actual secrets)
     * Should NOT show errors
     */
    public void placeholdersGood() {
        String example = "${DB_PASSWORD}";     // ← Placeholder
        String example2 = "YOUR_PASSWORD_HERE"; // ← Placeholder
    }
}
