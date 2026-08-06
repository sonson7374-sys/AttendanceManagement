package com.attendance.user.repository;

import com.attendance.user.domain.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByUserIdAndDeviceId(Long userId, String deviceId);
    List<UserDevice> findByUserIdOrderByLastSeenAtDesc(Long userId);
    boolean existsByUserIdAndDeviceIdAndActiveTrue(Long userId, String deviceId);
}
