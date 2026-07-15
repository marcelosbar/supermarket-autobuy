package com.autobuy.service;

import com.autobuy.provider.CredentialProvider;
import com.autobuy.provider.ShoppingListProvider;
import org.springframework.stereotype.Component;

/**
 * Groups shopping list and credentials providers to reduce constructor
 * dependency count.
 */
@Component
public class ExecutionProviders {

	private final CredentialProvider credentialProvider;
	private final ShoppingListProvider shoppingListProvider;

	public ExecutionProviders(CredentialProvider credentialProvider, ShoppingListProvider shoppingListProvider) {
		this.credentialProvider = credentialProvider;
		this.shoppingListProvider = shoppingListProvider;
	}

	public CredentialProvider getCredentialProvider() {
		return credentialProvider;
	}

	public ShoppingListProvider getShoppingListProvider() {
		return shoppingListProvider;
	}
}
