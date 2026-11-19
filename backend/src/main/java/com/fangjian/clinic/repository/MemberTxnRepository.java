package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.MemberTxn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberTxnRepository extends JpaRepository<MemberTxn, Long> {

    Page<MemberTxn> findByMemberIdOrderByIdDesc(Long memberId, Pageable pageable);
}
