package com.example.webtestnreport.repository;

import com.example.webtestnreport.model.TestRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestRunRepository extends JpaRepository<TestRun, Long> {
    List<TestRun> findByRuleIdOrderByStartedAtDesc(Long ruleId);
    List<TestRun> findByOrderByStartedAtDesc(Pageable pageable);
}
