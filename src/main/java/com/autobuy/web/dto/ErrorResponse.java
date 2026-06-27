package com.autobuy.web.dto;

import java.time.LocalDateTime;

public record ErrorResponse(String error, String type, LocalDateTime timestamp) {
}
