package com.example.webtestnreport.service;

import com.example.webtestnreport.model.TestRun;
import com.example.webtestnreport.model.Ticket;
import com.example.webtestnreport.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TicketService {

    @Autowired
    private TicketRepository ticketRepository;

    @Transactional
    public void processTestResult(TestRun run) {
        if ("FAILED".equals(run.getStatus())) {
            handleFailure(run);
        } else {
            handleSuccess(run);
        }
    }

    private void handleFailure(TestRun run) {
        // Look for an unresolved ticket for this rule
        Optional<Ticket> existingTicketOpt = ticketRepository.findFirstByRuleIdAndStatusNot(run.getRuleId(), "RESOLVED");

        if (existingTicketOpt.isPresent()) {
            Ticket ticket = existingTicketOpt.get();
            ticket.setTestRunId(run.getId());
            ticket.setUpdatedAt(LocalDateTime.now());
            ticket.setDescription("Failure persists in Run #" + run.getId() + "\nError: " + run.getErrorMessage() + "\n\n" + ticket.getDescription());
            ticketRepository.save(ticket);
        } else {
            Ticket newTicket = new Ticket();
            newTicket.setRuleId(run.getRuleId());
            newTicket.setRuleName(run.getRuleName());
            newTicket.setTestRunId(run.getId());
            newTicket.setTitle("UI Test Failure: " + run.getRuleName());
            newTicket.setSeverity("HIGH");
            newTicket.setStatus("OPEN");
            newTicket.setDescription(
                String.format("Test Rule '%s' failed in Run #%d.\n\nError Message:\n%s\n\nStarted At: %s\nDuration: %d ms",
                run.getRuleName(), run.getId(), run.getErrorMessage(), run.getStartedAt(), run.getDurationMs())
            );
            ticketRepository.save(newTicket);
        }
    }

    private void handleSuccess(TestRun run) {
        // Look for any unresolved ticket for this rule
        Optional<Ticket> existingTicketOpt = ticketRepository.findFirstByRuleIdAndStatusNot(run.getRuleId(), "RESOLVED");

        if (existingTicketOpt.isPresent()) {
            Ticket ticket = existingTicketOpt.get();
            ticket.setStatus("RESOLVED");
            ticket.setTestRunId(run.getId());
            ticket.setUpdatedAt(LocalDateTime.now());
            ticket.setDescription(
                "RESOLVED: Test passed successfully in Run #" + run.getId() + " at " + LocalDateTime.now() + ".\n\n" + ticket.getDescription()
            );
            ticketRepository.save(ticket);
        }
    }
}
