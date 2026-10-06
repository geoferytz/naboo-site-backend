package com.naboo;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity @Table(name="products")
public class Product extends BaseEntity {
    public String name, slug, shortDescription, description, category, imageUrl, status = "DRAFT", features = "";
    public boolean featured;
    public int displayOrder;
}
