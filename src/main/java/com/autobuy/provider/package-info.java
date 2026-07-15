/**
 * Abstractions and implementations for external data sources: credentials,
 * shopping lists, application settings, and folder selection.
 *
 * <h2>Constraints</h2>
 * <ul>
 * <li>Define one interface per provider contract. Name implementations with the
 * {@code Default} prefix.</li>
 * <li>Each implementation class must implement exactly one provider interface
 * (SRP/ISP).</li>
 * <li>May depend on: {@code model}, {@code exception}.</li>
 * <li>Never throw checked exceptions — wrap in the appropriate
 * {@link com.autobuy.exception.AutoBuyException} subclass.</li>
 * </ul>
 */
package com.autobuy.provider;
