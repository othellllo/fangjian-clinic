package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.Drug;
import com.fangjian.clinic.domain.enums.DrugCategory;
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

    /** 药品联想：名称/编码/拼音码模糊匹配（拼音支持首字母，如 hq → 黄芪/黄芩）。 */
    @Query("""
            select d from Drug d
            where d.status = 1
              and (d.name like concat('%', :kw, '%')
                   or d.code like concat('%', upper(:kw), '%')
                   or upper(d.pinyin) like concat('%', upper(:kw), '%'))
            order by d.name asc
            """)
    java.util.List<Drug> suggest(@Param("kw") String keyword,
                                 org.springframework.data.domain.Pageable pageable);

    java.util.Optional<Drug> findFirstByBarcode(String barcode);

    java.util.Optional<Drug> findFirstByCode(String code);
}
