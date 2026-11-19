package com.yiguan.his.repository;

import com.yiguan.his.domain.MemberTxn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberTxnRepository extends JpaRepository<MemberTxn, Long> {

    Page<MemberTxn> findByMemberIdOrderByIdDesc(Long memberId, Pageable pageable);
}
