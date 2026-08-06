package com.attendance.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "break_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BreakRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public BreakRecord(Long recordId, Instant startAt) {
        this.recordId = recordId;
        this.startAt = startAt;
    }

    public void end(Instant endAt) {
        this.endAt = endAt;
    }

    public boolean isOngoing() {
        return endAt == null;
    }

    public long durationMinutes() {
        if (endAt == null) return 0;
        return (endAt.getEpochSecond() - startAt.getEpochSecond()) / 60;
    }
}
