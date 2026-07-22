package com.autobuy.web.dto;

/**
 * DTO for save backup directory request.
 *
 * @param backupDir
 *            Directory path to save as backup location
 */
public record BackupDirRequest(String backupDir) {
}
