package com.autobuy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Logs historical prices of products.
 */
@Entity
@Table(name = "price_history")
public class PriceHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal price;

	@Column(nullable = false)
	private LocalDateTime recordedAt;

	@Column(nullable = false)
	private String source;

	// Constructors
	public PriceHistory() {
	}

	public PriceHistory(Product product, BigDecimal price, LocalDateTime recordedAt, String source) {
		this.product = product;
		this.price = price;
		this.recordedAt = recordedAt;
		this.source = source;
	}

	// Getters and Setters
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}

	public Product getProduct() {
		return product;
	}
	public void setProduct(Product product) {
		this.product = product;
	}

	public BigDecimal getPrice() {
		return price;
	}
	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public LocalDateTime getRecordedAt() {
		return recordedAt;
	}
	public void setRecordedAt(LocalDateTime recordedAt) {
		this.recordedAt = recordedAt;
	}

	public String getSource() {
		return source;
	}
	public void setSource(String source) {
		this.source = source;
	}

	@Override
	public String toString() {
		return "PriceHistory{" + "id=" + id + ", product=" + (product == null ? null : product.getId()) + ", price="
				+ price + ", recordedAt=" + recordedAt + ", source='" + source + '\'' + '}';
	}
}
