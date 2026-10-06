package com.naboo;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="inquiries")
public class Inquiry extends BaseEntity {
    public String referenceNumber, fullName, email, phone, company, subject, message, status = "NEW", notes;
    public UUID productId;
    public UUID assignedUserId;
    public Instant createdAt = Instant.now();
}
