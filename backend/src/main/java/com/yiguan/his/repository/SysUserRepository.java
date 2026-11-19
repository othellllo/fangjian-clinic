package com.yiguan.his.repository;

import com.yiguan.his.domain.SysUser;
import com.yiguan.his.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    List<SysUser> findByRoleOrderByRealNameAsc(UserRole role);

    List<SysUser> findByRoleAndStatusOrderByRealNameAsc(UserRole role, Integer status);
}
