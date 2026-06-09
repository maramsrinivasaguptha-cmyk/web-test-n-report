package com.example.webtestnreport.controller;

import com.example.webtestnreport.model.Ticket;
import com.example.webtestnreport.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@CrossOrigin(origins = "*")
public class TicketController {

    @Autowired
    private TicketRepository ticketRepository;

    @GetMapping
    public List<Ticket> getAllTickets() {
        return ticketRepository.findByOrderByCreatedAtDesc();
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Ticket> updateTicketStatus(@PathVariable Long id, @RequestParam String status) {
        return ticketRepository.findById(id)
                .map(ticket -> {
                    ticket.setStatus(status);
                    Ticket updated = ticketRepository.save(ticket);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/severity")
    public ResponseEntity<Ticket> updateTicketSeverity(@PathVariable Long id, @RequestParam String severity) {
        return ticketRepository.findById(id)
                .map(ticket -> {
                    ticket.setSeverity(severity);
                    Ticket updated = ticketRepository.save(ticket);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
