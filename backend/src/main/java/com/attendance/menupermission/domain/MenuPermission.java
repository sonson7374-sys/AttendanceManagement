package com.attendance.menupermission.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "menu_permissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"role", "menu_key", "action_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(name = "menu_key", nullable = false, length = 50)
    private String menuKey;

    @Column(name = "action_key", nullable = false, length = 50)
    private String actionKey;

    @Column(nullable = false)
    private boolean enabled;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public MenuPermission(String role, String menuKey, String actionKey, boolean enabled) {
        this.role = role;
        this.menuKey = menuKey;
        this.actionKey = actionKey;
        this.enabled = enabled;
    }

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
