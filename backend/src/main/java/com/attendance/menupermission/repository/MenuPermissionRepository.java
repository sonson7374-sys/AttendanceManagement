package com.attendance.menupermission.repository;

import com.attendance.menupermission.domain.MenuPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuPermissionRepository extends JpaRepository<MenuPermission, Long> {
    List<MenuPermission> findByRole(String role);
    Optional<MenuPermission> findByRoleAndMenuKeyAndActionKey(String role, String menuKey, String actionKey);
}
