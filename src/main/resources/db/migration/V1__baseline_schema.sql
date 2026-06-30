-- Flyway V1 Baseline Migration
-- Captures the exact schema that Hibernate generates from JPA entities
-- using Spring Boot's default SpringPhysicalNamingStrategy (camelCase -> snake_case).

CREATE TABLE IF NOT EXISTS products (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    external_id VARCHAR(255) NOT NULL,
    supermarket VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    brand       VARCHAR(255),
    url         VARCHAR(1000),
    category    VARCHAR(255),
    CONSTRAINT uq_products_external_id_supermarket UNIQUE (external_id, supermarket)
);

CREATE TABLE IF NOT EXISTS product_mappings (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    search_text         VARCHAR(255) NOT NULL,
    supermarket         VARCHAR(255) NOT NULL,
    external_product_id VARCHAR(255) NOT NULL,
    product_name        VARCHAR(255),
    CONSTRAINT uq_product_mappings_search_text_supermarket UNIQUE (search_text, supermarket)
);

CREATE TABLE IF NOT EXISTS price_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT       NOT NULL,
    price       DECIMAL(10, 2) NOT NULL,
    recorded_at TIMESTAMP    NOT NULL,
    source      VARCHAR(255) NOT NULL,
    CONSTRAINT fk_price_history_product FOREIGN KEY (product_id) REFERENCES products (id)
);
