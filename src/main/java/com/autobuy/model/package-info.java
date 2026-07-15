/**
 * Domain model: JPA entities, records (value objects), and enums.
 *
 * <h2>Constraints</h2>
 * <ul>
 * <li>No Spring annotations except JPA ({@code @Entity}, {@code @Column},
 * etc.).</li>
 * <li>No imports from any sibling package — this is the innermost layer.</li>
 * <li>Entities must implement {@code equals()}/{@code hashCode()} using
 * business keys.</li>
 * <li>Use Java records for immutable value objects and DTOs.</li>
 * </ul>
 */
package com.autobuy.model;
