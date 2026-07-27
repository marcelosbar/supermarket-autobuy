package com.autobuy.web.dto;

/**
 * DTO representing a database backup file metadata item.
 *
 * @param fileName
 *            Name of the backup file
 * @param sizeBytes
 *            Size of the file in bytes
 * @param lastModified
 *            ISO-8601 formatted string of the file's last modified timestamp
 */
public record BackupFileResponse(String fileName, long sizeBytes, String lastModified) {
}
