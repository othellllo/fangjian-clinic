package com.yiguan.his.repository;

import com.yiguan.his.domain.Drug;
import com.yiguan.his.domain.enums.DrugCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DrugRepository extends JpaRepository<Drug, Long> {

    @Query("""
            select d from Drug d
            where (:kw = '' or d.name like concat('%', :kw, '%')
                   or d.code like concat('%', :kw, '%')
                   or d.pinyin like concat('%', :kw, '%'))
              and (:category is null or d.category = :category)
              and (:status is null or d.status = :status)
            order by d.id desc
            """)
    Page<Drug> search(@Param("kw") String keyword,
                      @Param("category") DrugCategory category,
                      @Param("status") Integer status,
                      Pageable pageable);

    List<Drug> findByCategoryAndStatus(DrugCategory category, Integer status);

    boolean existsByCode(String code);

    java.util.Optional<Drug> findFirstByBarcode(String barcode);

    java.util.Optional<Drug> findFirstByCode(String code);
}
