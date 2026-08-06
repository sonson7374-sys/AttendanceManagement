package com.attendance.commoncode.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "common_codes", uniqueConstraints = @UniqueConstraint(columnNames = {"group_code", "code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommonCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_code", nullable = false, length = 50)
    private String groupCode;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "code_name", nullable = false, length = 100)
    private String codeName;

    @Column(length = 200)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    // DB 컬럼명은 protected이지만 Java 예약어라 필드명은 protectedCode로 매핑한다.
    @Column(name = "protected", nullable = false)
    private boolean protectedCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public CommonCode(String groupCode, String code, String codeName, String description,
                       int displayOrder, boolean active) {
        this.groupCode = groupCode;
        this.code = code;
        this.codeName = codeName;
        this.description = description;
        this.displayOrder = displayOrder;
        this.active = active;
        this.protectedCode = false;
    }

    public void update(String codeName, String description, int displayOrder, boolean active) {
        this.codeName = codeName;
        this.description = description;
        this.displayOrder = displayOrder;
        this.active = active;
    }
}
