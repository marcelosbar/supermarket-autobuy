package com.autobuy.model;

import jakarta.persistence.*;

/**
 * Represents a unique product in a supermarket.
 */
@Entity
@Table(name = "products", uniqueConstraints = {@UniqueConstraint(columnNames = {"externalId", "supermarket"})})
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String externalId;

	@Column(nullable = false)
	private String supermarket;

	@Column(nullable = false)
	private String name;

	private String brand;

	@Column(length = 1000)
	private String url;

	private String category;

	// Constructors
	public Product() {
	}

	public Product(String externalId, String supermarket, String name, String brand, String url, String category) {
		this.externalId = externalId;
		this.supermarket = supermarket;
		this.name = name;
		this.brand = brand;
		this.url = url;
		this.category = category;
	}

	// Getters and Setters
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}

	public String getExternalId() {
		return externalId;
	}
	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public String getSupermarket() {
		return supermarket;
	}
	public void setSupermarket(String supermarket) {
		this.supermarket = supermarket;
	}

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	public String getBrand() {
		return brand;
	}
	public void setBrand(String brand) {
		this.brand = brand;
	}

	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}

	public String getCategory() {
		return category;
	}
	public void setCategory(String category) {
		this.category = category;
	}

	@Override
	public String toString() {
		return "Product{" + "id=" + id + ", externalId='" + externalId + '\'' + ", supermarket='" + supermarket + '\''
				+ ", name='" + name + '\'' + ", brand='" + brand + '\'' + ", url='" + url + '\'' + ", category='"
				+ category + '\'' + '}';
	}
}
