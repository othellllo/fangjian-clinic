package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:module is null or a.module = :module)
              and (:kw = '' or a.username like concat('%', :kw, '%')
                   or a.realName like concat('%', :kw, '%')
                   or a.action like concat('%', :kw, '%'))
              and (:from is null or a.createdAt >= :from)
              and (:to is null or a.createdAt < :to)
            order by a.id desc
            """)
    Page<AuditLog> search(@Param("module") String module,
                          @Param("kw") String keyword,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          Pageable pageable);

    @Query("select distinct a.module from AuditLog a order by a.module")
    List<String> findModules();
}
