package com.attendance.auth.service;

import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserPrincipal;
import com.attendance.user.domain.UserStatus;
import com.attendance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AttendanceException(ErrorCode.USER_NOT_FOUND));
        return toUserPrincipal(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.USER_NOT_FOUND));
        return toUserPrincipal(user);
    }

    private UserPrincipal toUserPrincipal(User user) {
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AttendanceException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new AttendanceException(ErrorCode.ACCOUNT_INACTIVE);
        }
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getLevel(),
                user.getCompanyId(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
