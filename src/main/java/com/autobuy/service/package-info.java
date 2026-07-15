/**
 * Core business services with transactional boundaries.
 *
 * <h2>Constraints</h2>
 * <ul>
 * <li>No interfaces needed — inject concrete {@code @Service} types
 * directly.</li>
 * <li>May depend on: {@code repository}, {@code model}, {@code provider},
 * {@code driver} (interface only), {@code exception}.</li>
 * <li>Must NOT depend on: {@code web}, {@code config}.</li>
 * <li>Annotate state-modifying methods with {@code @Transactional}.</li>
 * <li>Target max 300 lines per class, max 5 constructor dependencies.</li>
 * </ul>
 */
package com.autobuy.service;
