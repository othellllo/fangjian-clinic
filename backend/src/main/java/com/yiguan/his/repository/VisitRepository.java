package com.yiguan.his.repository;

import com.yiguan.his.domain.Visit;
import com.yiguan.his.domain.enums.VisitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface VisitRepository extends JpaRepository<Visit, Long> {

    List<Visit> findByDoctorIdAndStatusInOrderByRegisterTimeAsc(Long doctorId, Collection<VisitStatus> statuses);

    List<Visit> findByPatientIdOrderByRegisterTimeDesc(Long patientId);

    /** 某患者某天的就诊数，用于导入时判定是否已存在。 */
    @Query("select count(v) from Visit v where v.patientId = :patientId and v.registerTime >= :from and v.registerTime < :to")
    long countByPatientAndDay(@Param("patientId") Long patientId,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);

    List<Visit> findByPatientIdAndRegisterTimeBetweenOrderByIdAsc(Long patientId,
                                                                  LocalDateTime from, LocalDateTime to);

    Page<Visit> findByPatientIdOrderByRegisterTimeDesc(Long patientId, Pageable pageable);

    @Query("""
            select v from Visit v
            where (:doctorId is null or v.doctorId = :doctorId)
              and (:status is null or v.status = :status)
              and (:from is null or v.registerTime >= :from)
              and (:to is null or v.registerTime < :to)
            order by v.registerTime desc
            """)
    Page<Visit> search(@Param("doctorId") Long doctorId,
                       @Param("status") VisitStatus status,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to,
                       Pageable pageable);

    @Query("select coalesce(max(v.queueNo), 0) from Visit v where v.doctorId = :doctorId and v.registerTime >= :from and v.registerTime < :to")
    Integer findMaxQueueNo(@Param("doctorId") Long doctorId,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to);

    long countByRegisterTimeBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusAndRegisterTimeBetween(VisitStatus status, LocalDateTime from, LocalDateTime to);
}
