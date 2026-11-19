package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.Appointment;
import com.fangjian.clinic.domain.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("""
            select a from Appointment a
            where (:date is null or a.appointDate = :date)
              and (:status is null or a.status = :status)
              and (:doctorId is null or a.doctorId = :doctorId)
            order by a.appointDate asc, a.timeSlot asc, a.id asc
            """)
    Page<Appointment> search(@Param("date") LocalDate date,
                             @Param("status") AppointmentStatus status,
                             @Param("doctorId") Long doctorId,
                             Pageable pageable);

    List<Appointment> findByPatientIdOrderByAppointDateDescTimeSlotAsc(Long patientId);

    long countByDoctorIdAndAppointDateAndTimeSlotAndStatusNot(Long doctorId, LocalDate appointDate,
                                                              String timeSlot, AppointmentStatus status);

    boolean existsByPatientIdAndDoctorIdAndAppointDateAndTimeSlotAndStatusNot(Long patientId, Long doctorId,
                                                                             LocalDate appointDate, String timeSlot,
                                                                             AppointmentStatus status);
}
