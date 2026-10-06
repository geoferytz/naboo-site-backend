package com.naboo;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="audit_logs")
public class AuditLog extends BaseEntity {
    public String actor, action;
    public UUID entityId;
    public Instant createdAt = Instant.now();
    protected AuditLog() {}
    AuditLog(String actor, String action, UUID entityId) { this.actor = actor; this.action = action; this.entityId = entityId; }
}
