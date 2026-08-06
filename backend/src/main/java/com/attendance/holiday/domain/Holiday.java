package com.attendance.holiday.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "holidays")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate holidayDate;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "holiday_type", nullable = false, length = 20)
    private HolidayType holidayType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public Holiday(LocalDate holidayDate, String name, HolidayType holidayType) {
        this.holidayDate = holidayDate;
        this.name = name;
        this.holidayType = holidayType != null ? holidayType : HolidayType.PUBLIC;
    }

    public void update(String name, HolidayType holidayType) {
        this.name = name;
        this.holidayType = holidayType != null ? holidayType : HolidayType.PUBLIC;
    }
}
