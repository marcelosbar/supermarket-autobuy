/**
 * Browser automation: the {@link com.autobuy.driver.SupermarketDriver}
 * interface and store-specific Playwright implementations.
 *
 * <h2>Constraints</h2>
 * <ul>
 * <li>{@link com.autobuy.driver.SupermarketDriver} is the public contract —
 * only this interface may be imported by other packages.</li>
 * <li>Store-specific classes must be package-private or in a subpackage.</li>
 * <li>May depend on: {@code model}, {@code exception}.</li>
 * <li>Throw {@link com.autobuy.exception.DriverException} for all driver
 * failures.</li>
 * </ul>
 */
package com.autobuy.driver;
