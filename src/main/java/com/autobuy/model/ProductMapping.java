package com.autobuy.model;

import jakarta.persistence.*;

/**
 * Maps a generic search query to an exact supermarket SKU.
 */
@Entity
@Table(name = "product_mappings", uniqueConstraints = {@UniqueConstraint(columnNames = {"searchText", "supermarket"})})
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

	// Constructors
	public ProductMapping() {
	}

	public ProductMapping(String searchText, String supermarket, String externalProductId, String productName) {
		this.searchText = searchText;
		this.supermarket = supermarket;
		this.externalProductId = externalProductId;
		this.productName = productName;
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

	@Override
	public String toString() {
		return "ProductMapping{" + "id=" + id + ", searchText='" + searchText + '\'' + ", supermarket='" + supermarket
				+ '\'' + ", externalProductId='" + externalProductId + '\'' + ", productName='" + productName + '\''
				+ '}';
	}
}
