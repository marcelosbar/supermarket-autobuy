package com.autobuy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Generic response DTO for web API operations.
 *
 * @param success
 *            Indicates if the operation succeeded
 * @param message
 *            Optional message providing additional context
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActionResponse(boolean success, String message) {

	public ActionResponse(boolean success) {
		this(success, null);
	}
}
