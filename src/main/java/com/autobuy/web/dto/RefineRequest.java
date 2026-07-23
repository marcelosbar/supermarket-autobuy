package com.autobuy.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RefineRequest(@NotBlank String query) {
}
