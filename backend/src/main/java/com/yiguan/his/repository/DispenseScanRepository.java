package com.yiguan.his.repository;

import com.yiguan.his.domain.DispenseScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DispenseScanRepository extends JpaRepository<DispenseScan, Long> {

    List<DispenseScan> findByPrescriptionIdOrderByIdAsc(Long prescriptionId);

    void deleteByPrescriptionId(Long prescriptionId);
}