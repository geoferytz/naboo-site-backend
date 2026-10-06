package com.naboo;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name="pricing_packages")
public class PackagePlan extends BaseEntity {
    public UUID productId;
    public String name, description, currency = "TSh", billingFrequency = "MONTHLY", features = "";
    public String userLimit, roomLimit, productLimit, supportLevel;
    public BigDecimal price;
    public boolean customPrice, active = true, featured;
    public int displayOrder;
}
