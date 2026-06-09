package com.example.webtestnreport.repository;

import com.example.webtestnreport.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findFirstByRuleIdAndStatusNot(Long ruleId, String status);
    List<Ticket> findByOrderByCreatedAtDesc();
    List<Ticket> findByStatusNotOrderByCreatedAtDesc(String status);
}
