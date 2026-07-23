package com.autobuy.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveRequest(@NotBlank String externalId, boolean saveMapping) {
}
