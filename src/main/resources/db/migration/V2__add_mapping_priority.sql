-- V2 Migration: Add mapping priority and fallback link
ALTER TABLE product_mappings ADD COLUMN priority INT NOT NULL DEFAULT 0;
ALTER TABLE product_mappings ADD COLUMN fallback_for_id BIGINT;

ALTER TABLE product_mappings DROP CONSTRAINT uq_product_mappings_search_text_supermarket;
ALTER TABLE product_mappings ADD CONSTRAINT uq_product_mappings_search_text_supermarket_priority UNIQUE (search_text, supermarket, priority);
