package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    Optional<MedicalRecord> findByVisitId(Long visitId);
}
