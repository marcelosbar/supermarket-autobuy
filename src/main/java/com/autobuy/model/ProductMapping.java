package com.autobuy.model;

import jakarta.persistence.*;
import java.util.Objects;

/**
 * Maps a generic search query to an exact supermarket SKU.
 */
@Entity
@Table(name = "product_mappings", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"searchText", "supermarket", "priority"})})
public class ProductMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String searchText;

	@Column(nullable = false)
	private String supermarket;

	@Column(nullable = false)
	private String externalProductId;

	private String productName;

	@Column(nullable = false)
	private int priority = 0;

	private Long fallbackForId;

	// Constructors
	public ProductMapping() {
	}

	public ProductMapping(String searchText, String supermarket, String externalProductId, String productName) {
		this.searchText = searchText;
		this.supermarket = supermarket;
		this.externalProductId = externalProductId;
		this.productName = productName;
		this.priority = 0;
	}

	// Getters and Setters
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}

	public String getSearchText() {
		return searchText;
	}
	public void setSearchText(String searchText) {
		this.searchText = searchText;
	}

	public String getSupermarket() {
		return supermarket;
	}
	public void setSupermarket(String supermarket) {
		this.supermarket = supermarket;
	}

	public String getExternalProductId() {
		return externalProductId;
	}
	public void setExternalProductId(String externalProductId) {
		this.externalProductId = externalProductId;
	}

	public String getProductName() {
		return productName;
	}
	public void setProductName(String productName) {
		this.productName = productName;
	}

	public int getPriority() {
		return priority;
	}
	public void setPriority(int priority) {
		this.priority = priority;
	}

	public Long getFallbackForId() {
		return fallbackForId;
	}
	public void setFallbackForId(Long fallbackForId) {
		this.fallbackForId = fallbackForId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ProductMapping productMapping = (ProductMapping) o;
		return priority == productMapping.priority && Objects.equals(searchText, productMapping.searchText)
				&& Objects.equals(supermarket, productMapping.supermarket);
	}

	@Override
	public int hashCode() {
		return Objects.hash(searchText, supermarket, priority);
	}

	@Override
	public String toString() {
		return "ProductMapping{" + "id=" + id + ", searchText='" + searchText + '\'' + ", supermarket='" + supermarket
				+ '\'' + ", externalProductId='" + externalProductId + '\'' + ", productName='" + productName + '\''
				+ ", priority=" + priority + ", fallbackForId=" + fallbackForId + '}';
	}
}
