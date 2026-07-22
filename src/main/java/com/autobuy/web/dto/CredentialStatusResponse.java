package com.autobuy.web.dto;

/**
 * DTO representing credential status for a supermarket.
 *
 * @param supermarket
 *            Uppercased supermarket name
 * @param hasUsername
 *            True if a non-blank username is configured
 * @param hasPassword
 *            True if a non-blank password is configured
 * @param username
 *            Configured username or empty string
 */
public record CredentialStatusResponse(String supermarket, boolean hasUsername, boolean hasPassword, String username) {
}
