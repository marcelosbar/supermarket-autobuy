package com.autobuy.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AddAlternativeRequest(@NotBlank String searchText, @NotBlank String supermarket,
		@NotBlank String externalId, @NotBlank String productName) {
}
