/**
 * Request and response DTO records for the REST API.
 *
 * <h2>Constraints</h2>
 * <ul>
 * <li>Use Java {@code record} types exclusively — no mutable classes.</li>
 * <li>May depend on: {@code model} (for shared enums/value objects).</li>
 * <li>Add Bean Validation annotations ({@code @NotNull}, {@code @NotBlank}) to
 * request DTOs.</li>
 * <li>Only place REST API-facing DTOs here. Internal service-to-service data
 * classes go in {@code model}.</li>
 * </ul>
 */
package com.autobuy.web.dto;
