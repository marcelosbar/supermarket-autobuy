package com.autobuy.web.dto;

public record AddAlternativeRequest(String searchText, String supermarket, String externalId, String productName) {
}
