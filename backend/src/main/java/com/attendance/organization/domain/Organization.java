package com.attendance.organization.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "organizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public Organization(Long companyId, Long parentId, String name, Integer displayOrder) {
        this.companyId = companyId;
        this.parentId = parentId;
        this.name = name;
        this.displayOrder = displayOrder;
        this.active = true;
    }

    public void update(String name, Long parentId, Integer displayOrder) {
        this.name = name;
        this.parentId = parentId;
        this.displayOrder = displayOrder;
    }

    public void deactivate() {
        this.active = false;
    }
}
