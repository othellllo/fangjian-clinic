package com.fangjian.clinic.service;

import com.fangjian.clinic.common.BusinessException;
import com.fangjian.clinic.common.PageResult;
import com.fangjian.clinic.domain.SysUser;
import com.fangjian.clinic.domain.enums.UserRole;
import com.fangjian.clinic.dto.MemberDto;
import com.fangjian.clinic.repository.SysUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 员工账号管理。 */
@Service
public class UserService {

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(SysUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public static MemberDto.User toDto(SysUser user) {
        return new MemberDto.User(user.getId(), user.getUsername(), user.getRealName(), user.getRole(),
                user.getTitle(), user.getDept(), user.getPhone(), user.getStatus());
    }

    public List<MemberDto.User> doctors() {
        return userRepository.findByRoleAndStatusOrderByRealNameAsc(UserRole.DOCTOR, 1)
                .stream().map(UserService::toDto).toList();
    }

    public PageResult<MemberDto.User> page(int page, int size) {
        Page<SysUser> result = userRepository.findAll(
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(result, UserService::toDto);
    }

    @Transactional
    public MemberDto.User create(String username, String password, String realName,
                                 UserRole role, String title, String dept, String phone) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BusinessException("用户名和密码不能为空");
        }
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRealName(realName);
        user.setRole(role == null ? UserRole.CASHIER : role);
        user.setTitle(title);
        user.setDept(dept);
        user.setPhone(phone);
        user.setStatus(1);
        return toDto(userRepository.save(user));
    }

    @Transactional
    public MemberDto.User update(Long id, String realName, UserRole role, String title,
                                 String dept, String phone, String password) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("员工"));
        if (StringUtils.hasText(realName)) {
            user.setRealName(realName);
        }
        if (role != null) {
            user.setRole(role);
        }
        user.setTitle(title);
        user.setDept(dept);
        user.setPhone(phone);
        if (StringUtils.hasText(password)) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void updateStatus(Long id, Integer status) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("员工"));
        user.setStatus(status == null || status == 0 ? 0 : 1);
        userRepository.save(user);
    }
}
