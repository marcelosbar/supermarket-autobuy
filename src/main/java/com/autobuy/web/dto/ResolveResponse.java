package com.autobuy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for interactive resolution operations.
 *
 * @param success
 *            Indicates if the resolution API call succeeded
 * @param added
 *            Indicates if the product was successfully added to cart
 * @param message
 *            Detail message regarding resolution status
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResolveResponse(boolean success, boolean added, String message) {
}
