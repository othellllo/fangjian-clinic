package com.yiguan.his.repository;

import com.yiguan.his.domain.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findFirstByPhoneOrderByIdAsc(String phone);

    List<Patient> findByPhone(String phone);

    @Query("""
            select p from Patient p
            where (:kw = '' or p.name like concat('%', :kw, '%')
                   or p.phone like concat('%', :kw, '%')
                   or p.patientNo like concat('%', :kw, '%'))
            order by p.id desc
            """)
    Page<Patient> search(@Param("kw") String keyword, Pageable pageable);
}
