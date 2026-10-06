package com.naboo;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="orders")
public class Order extends BaseEntity {
    public String referenceNumber, fullName, email, phone, company, paymentMethod, paymentReference, payerPhone, currency, status = "AWAITING_PAYMENT", notes;
    public BigDecimal totalAmount;
    public Instant createdAt = Instant.now();
    public Instant paidAt;
}
