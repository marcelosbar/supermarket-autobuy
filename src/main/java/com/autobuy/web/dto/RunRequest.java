package com.autobuy.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RunRequest(@NotBlank String supermarket, Boolean headless) {
}
