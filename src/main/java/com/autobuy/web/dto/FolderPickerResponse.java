package com.autobuy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing native folder chooser response.
 *
 * @param success
 *            True if directory selection succeeded
 * @param path
 *            Selected directory absolute path, or null if cancelled
 * @param message
 *            Message describing status or reason if cancelled
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FolderPickerResponse(boolean success, String path, String message) {

	public FolderPickerResponse(boolean success, String path) {
		this(success, path, null);
	}
}
