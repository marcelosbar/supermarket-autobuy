/**
 * REST controllers, exception handlers, and web infrastructure.
 *
 * <h2>Constraints</h2>
 * <ul>
 * <li>Controllers delegate ALL business logic to services — no calculations, no
 * conditional branching on domain state.</li>
 * <li>May depend on: {@code service}, {@code model}, {@code web/dto},
 * {@code exception}.</li>
 * <li>All responses must use typed DTO records from {@code web/dto/} — never
 * {@code Map<String, Object>}.</li>
 * <li>Error handling flows through {@code GlobalExceptionHandler} — controllers
 * must NOT catch-and-wrap exceptions.</li>
 * <li>Validate request DTOs with {@code @Valid}.</li>
 * </ul>
 */
package com.autobuy.web;
