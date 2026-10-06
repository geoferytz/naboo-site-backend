package com.naboo;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity @Table(name="users")
public class AdminUser extends BaseEntity {
    public String email, passwordHash, role = "ADMIN";
    public boolean active = true;
}
