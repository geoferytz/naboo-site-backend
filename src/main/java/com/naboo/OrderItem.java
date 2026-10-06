package com.naboo;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name="order_items")
public class OrderItem extends BaseEntity {
    public UUID orderId, packageId;
    public String productName, packageName, billingFrequency;
    public BigDecimal unitPrice;
    public int quantity = 1;
}
