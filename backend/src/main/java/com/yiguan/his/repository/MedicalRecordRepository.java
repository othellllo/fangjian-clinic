package com.yiguan.his.repository;

import com.yiguan.his.domain.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    Optional<MedicalRecord> findByVisitId(Long visitId);
}
