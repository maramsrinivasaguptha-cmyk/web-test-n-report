package com.example.webtestnreport.controller;

import com.example.webtestnreport.model.TestRule;
import com.example.webtestnreport.model.TestRun;
import com.example.webtestnreport.repository.TestRuleRepository;
import com.example.webtestnreport.repository.TestRunRepository;
import com.example.webtestnreport.service.PlaywrightTestRunner;
import com.example.webtestnreport.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/rules")
@CrossOrigin(origins = "*")
public class TestRuleController {

    @Autowired
    private TestRuleRepository ruleRepository;

    @Autowired
    private PlaywrightTestRunner testRunner;

    @Autowired
    private TestRunRepository runRepository;

    @Autowired
    private TicketService ticketService;

    @GetMapping
    public List<TestRule> getAllRules() {
        return ruleRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TestRule> getRuleById(@PathVariable Long id) {
        return ruleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public TestRule createRule(@RequestBody TestRule rule) {
        return ruleRepository.save(rule);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TestRule> updateRule(@PathVariable Long id, @RequestBody TestRule ruleDetails) {
        return ruleRepository.findById(id)
                .map(rule -> {
                    rule.setName(ruleDetails.getName());
                    rule.setDescription(ruleDetails.getDescription());
                    rule.setScript(ruleDetails.getScript());
                    rule.setIntervalMinutes(ruleDetails.getIntervalMinutes());
                    rule.setActive(ruleDetails.isActive());
                    TestRule updatedRule = ruleRepository.save(rule);
                    return ResponseEntity.ok(updatedRule);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        return ruleRepository.findById(id)
                .map(rule -> {
                    ruleRepository.delete(rule);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Run a rule immediately and return the test run details (Synchronous)
    @PostMapping("/{id}/run")
    public ResponseEntity<TestRun> runRuleImmediately(@PathVariable Long id) {
        Optional<TestRule> ruleOpt = ruleRepository.findById(id);
        if (ruleOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TestRule rule = ruleOpt.get();
        TestRun run = testRunner.runTest(rule);
        runRepository.save(run);
        ticketService.processTestResult(run);
        return ResponseEntity.ok(run);
    }
}
