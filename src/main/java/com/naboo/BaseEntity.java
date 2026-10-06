package com.naboo;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity {
    @Id public UUID id = UUID.randomUUID();
}
