package com.yiguan.his.repository;

import com.yiguan.his.domain.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Long> {

    List<PrescriptionItem> findByPrescriptionId(Long prescriptionId);

    List<PrescriptionItem> findByPrescriptionIdIn(Collection<Long> prescriptionIds);
}
