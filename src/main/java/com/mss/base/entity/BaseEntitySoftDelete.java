package com.mss.base.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.OffsetDateTime;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntitySoftDelete extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    @Column(name = "deleted_at")
    protected OffsetDateTime deletedAt;
}