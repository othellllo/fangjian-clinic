package com.yiguan.his.repository;

import com.yiguan.his.domain.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query("""
            select m from Member m
            where (:kw = '' or m.name like concat('%', :kw, '%')
                   or m.phone like concat('%', :kw, '%')
                   or m.memberNo like concat('%', :kw, '%'))
            order by m.id desc
            """)
    Page<Member> search(@Param("kw") String keyword, Pageable pageable);

    Optional<Member> findByPatientId(Long patientId);

    boolean existsByPatientId(Long patientId);
}
