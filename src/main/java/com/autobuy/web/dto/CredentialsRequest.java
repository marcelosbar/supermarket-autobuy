package com.autobuy.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CredentialsRequest(@NotBlank String supermarket, @NotBlank String username, @NotBlank String password) {
}
