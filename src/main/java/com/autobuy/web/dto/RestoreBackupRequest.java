package com.autobuy.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO requesting a database backup restoration.
 *
 * @param fileName
 *            Target backup file name to restore from
 */
public record RestoreBackupRequest(@NotBlank String fileName) {
}
