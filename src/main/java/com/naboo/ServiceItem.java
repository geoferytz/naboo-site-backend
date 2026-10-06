package com.naboo;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity @Table(name="services")
public class ServiceItem extends BaseEntity {
    public String name, slug, shortDescription, description, imageUrl, status = "DRAFT";
    public String category = "General", priceType = "QUOTE", currency = "TSh";
    public BigDecimal startingPrice;
    public int displayOrder;
}
