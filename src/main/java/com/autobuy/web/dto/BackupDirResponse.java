package com.autobuy.web.dto;

/**
 * DTO representing backup directory response.
 *
 * @param backupDir
 *            Configured backup directory path or empty string
 */
public record BackupDirResponse(String backupDir) {
}
