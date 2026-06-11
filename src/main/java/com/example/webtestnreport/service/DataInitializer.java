package com.example.webtestnreport.service;

import com.example.webtestnreport.model.TestRule;
import com.example.webtestnreport.repository.TestRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private TestRuleRepository ruleRepository;

    @Override
    public void run(String... args) {
        if (!ruleExists("Check Example Domain")) {
            TestRule r1 = new TestRule();
            r1.setName("Check Example Domain");
            r1.setDescription("Verifies the basic content of example.com is loading properly.");
            r1.setScript("goto https://example.com\nassert-text h1 = Example Domain\nassert-exists a");
            r1.setIntervalMinutes(5);
            r1.setActive(true);
            ruleRepository.save(r1);
        }

        if (!ruleExists("Verify Google Homepage")) {
            TestRule r2 = new TestRule();
            r2.setName("Verify Google Homepage");
            r2.setDescription("Validates that Google search page loads and title is correct.");
            r2.setScript("goto https://www.google.com\nassert-title = Google");
            r2.setIntervalMinutes(10);
            r2.setActive(true);
            ruleRepository.save(r2);
        }
        
        if (!ruleExists("Failing Test Demonstration")) {
            TestRule r3 = new TestRule();
            r3.setName("Failing Test Demonstration");
            r3.setDescription("A sample rule designed to fail to demonstrate screenshot capture and ticket creation.");
            r3.setScript("goto https://example.com\nassert-text h1 = This Will Fail Surely");
            r3.setIntervalMinutes(15);
            r3.setActive(false); // Keep inactive by default, but ready for manual test
            ruleRepository.save(r3);
        }

        if (!ruleExists("E2E Pipeline - API & Local DB Validation")) {
            TestRule r4 = new TestRule();
            r4.setName("E2E Pipeline - API & Local DB Validation");
            r4.setDescription("A multi-stage E2E pipeline that fetches rules from HTTP API, stores variables, and verifies against the local H2 SQL Database.");
            r4.setScript("stage Get Active Rules\nhttp-get /api/rules\nassert-status = 200\nstore-json 0.name = firstRuleName\n\nstage Query SQL Database\ndb-connect default\ndb-query SELECT name FROM test_rules LIMIT 1\nassert-db name = ${firstRuleName}");
            r4.setIntervalMinutes(10);
            r4.setActive(true);
            ruleRepository.save(r4);
        }

        if (!ruleExists("E2E Pipeline - NoSQL to API to SQL")) {
            TestRule r5 = new TestRule();
            r5.setName("E2E Pipeline - NoSQL to API to SQL");
            r5.setDescription("A complete chained E2E pipeline: inserts into NoSQL, retrieves data, POSTs via REST API, queries SQL DB to verify creation, and cleans up.");
            r5.setScript("stage Populate NoSQL Database\nnosql-insert carts = {\"itemId\": \"item_999\", \"qty\": \"5\", \"status\": \"pending\"}\nnosql-find carts = {\"itemId\": \"item_999\"}\nassert-nosql qty = 5\nstore-nosql itemId = currentItem\nstore-nosql qty = currentQty\n\nstage Call API Integration\nheader Content-Type = application/json\nhttp-post /api/rules = {\"name\": \"Simulated Rule ${currentItem}\", \"description\": \"Qty: ${currentQty}\", \"intervalMinutes\": 10, \"script\": \"wait 1000\", \"active\": false}\nassert-status = 200\nstore-json id = newRuleId\n\nstage SQL Verification\ndb-connect\ndb-query SELECT name, description FROM test_rules WHERE id = ${newRuleId}\nassert-db name = Simulated Rule item_999\nassert-db description = Qty: 5\n\nstage NoSQL Cleanup\ndb-execute DELETE FROM test_rules WHERE id = ${newRuleId}");
            r5.setIntervalMinutes(15);
            r5.setActive(true);
            ruleRepository.save(r5);
        }
    }

    private boolean ruleExists(String name) {
        return ruleRepository.findAll().stream().anyMatch(r -> r.getName().equals(name));
    }
}
