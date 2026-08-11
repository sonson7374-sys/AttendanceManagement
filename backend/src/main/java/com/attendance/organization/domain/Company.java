package com.attendance.organization.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "companies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "business_number", length = 20)
    private String businessNumber;

    @Column(length = 200)
    private String address;

    @Column(length = 20)
    private String phone;

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
    public Company(String name, String businessNumber, String address, String phone) {
        this.name = name;
        this.businessNumber = businessNumber;
        this.address = address;
        this.phone = phone;
        this.active = true;
    }

    public void update(String name, String businessNumber, String address, String phone) {
        this.name = name;
        this.businessNumber = businessNumber;
        this.address = address;
        this.phone = phone;
    }

    public void deactivate() {
        this.active = false;
    }
}
