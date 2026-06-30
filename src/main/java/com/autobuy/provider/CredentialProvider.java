package com.autobuy.provider;

import com.autobuy.exception.CredentialException;

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

	/**
	 * Saves credentials for a supermarket.
	 *
	 * @param supermarket
	 *            The name of the supermarket (e.g., "CONTINENTE")
	 * @param username
	 *            The username to save
	 * @param password
	 *            The password to save
	 * @throws CredentialException
	 *             If saving credentials fails
	 */
	default void saveCredentials(String supermarket, String username, String password) throws CredentialException {
		throw new UnsupportedOperationException("Saving credentials is not supported by this provider.");
	}
}
