package com.example.webtestnreport.repository;

import com.example.webtestnreport.model.TestRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestRuleRepository extends JpaRepository<TestRule, Long> {
    List<TestRule> findByActive(boolean active);
}
