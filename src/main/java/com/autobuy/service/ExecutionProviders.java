package com.autobuy.service;

import com.autobuy.provider.CredentialProvider;
import com.autobuy.provider.ShoppingListProvider;
import org.springframework.stereotype.Component;

/**
 * Groups providers and auxiliary services to reduce constructor dependency
 * count.
 */
@Component
public class ExecutionProviders {

	private final CredentialProvider credentialProvider;
	private final ShoppingListProvider shoppingListProvider;
	private final GuestSearchService guestSearchService;

	public ExecutionProviders(CredentialProvider credentialProvider, ShoppingListProvider shoppingListProvider,
			GuestSearchService guestSearchService) {
		this.credentialProvider = credentialProvider;
		this.shoppingListProvider = shoppingListProvider;
		this.guestSearchService = guestSearchService;
	}

	public CredentialProvider getCredentialProvider() {
		return credentialProvider;
	}

	public ShoppingListProvider getShoppingListProvider() {
		return shoppingListProvider;
	}

	public GuestSearchService getGuestSearchService() {
		return guestSearchService;
	}
}
