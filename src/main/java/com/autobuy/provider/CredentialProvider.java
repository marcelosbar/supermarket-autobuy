package com.autobuy.provider;

/**
 * Interface for loading credentials for a supermarket.
 */
public interface CredentialProvider {
	/**
	 * Gets the username (email) for the specified supermarket.
	 *
	 * @param supermarket
	 *            The name of the supermarket (e.g., "CONTINENTE")
	 * @return The username, or null if not found
	 */
	String getUsername(String supermarket);

	/**
	 * Gets the password for the specified supermarket.
	 *
	 * @param supermarket
	 *            The name of the supermarket (e.g., "CONTINENTE")
	 * @return The password, or null if not found
	 */
	String getPassword(String supermarket);
}
