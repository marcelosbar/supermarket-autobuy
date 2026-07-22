package com.autobuy.web.dto;

/**
 * DTO representing database backup status.
 *
 * @param backupDir
 *            Configured backup directory path or empty string
 * @param isConfigured
 *            True if a valid backup directory is configured
 */
public record BackupStatusResponse(String backupDir, boolean isConfigured) {
}
