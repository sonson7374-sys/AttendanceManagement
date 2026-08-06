package com.attendance.workplace.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "workplaces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workplace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String address;

    @Column(name = "detail_address", length = 200)
    private String detailAddress;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "radius_meters", nullable = false)
    private Integer radiusMeters;

    @Column(name = "max_accuracy_meters")
    private Integer maxAccuracyMeters;

    @Column(name = "check_in_allowed", nullable = false)
    private boolean checkInAllowed = true;

    @Column(name = "check_out_allowed", nullable = false)
    private boolean checkOutAllowed = true;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

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
    public Workplace(Long companyId, String name, String address, String detailAddress, String type,
                     BigDecimal latitude, BigDecimal longitude, Integer radiusMeters, Integer maxAccuracyMeters,
                     Boolean checkInAllowed, Boolean checkOutAllowed,
                     LocalDate validFrom, LocalDate validTo) {
        this.companyId = companyId;
        this.name = name;
        this.address = address;
        this.detailAddress = detailAddress;
        this.type = type != null ? type : "OFFICE";
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters != null ? radiusMeters : 100;
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.checkInAllowed = checkInAllowed == null || checkInAllowed;
        this.checkOutAllowed = checkOutAllowed == null || checkOutAllowed;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.active = true;
    }

    public void update(String name, String address, String detailAddress, String type,
                       BigDecimal latitude, BigDecimal longitude,
                       Integer radiusMeters, Integer maxAccuracyMeters,
                       boolean checkInAllowed, boolean checkOutAllowed,
                       LocalDate validFrom, LocalDate validTo) {
        this.name = name;
        this.address = address;
        this.detailAddress = detailAddress;
        this.type = type != null ? type : this.type;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters;
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.checkInAllowed = checkInAllowed;
        this.checkOutAllowed = checkOutAllowed;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public boolean isActiveOn(LocalDate date) {
        if (!this.active) return false;
        if (validFrom != null && date.isBefore(validFrom)) return false;
        if (validTo != null && date.isAfter(validTo)) return false;
        return true;
    }
}
