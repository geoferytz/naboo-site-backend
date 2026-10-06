package com.naboo;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="demo_requests")
public class DemoRequest extends BaseEntity {
    public String referenceNumber, fullName, companyName, email, phone, preferredDate, preferredTime, businessType, additionalInformation, status = "PENDING", notes;
    public UUID productId;
    public UUID assignedUserId;
    public Instant createdAt = Instant.now();
}
