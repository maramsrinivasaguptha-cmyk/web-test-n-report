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
        if (ruleRepository.count() == 0) {
            TestRule r1 = new TestRule();
            r1.setName("Check Example Domain");
            r1.setDescription("Verifies the basic content of example.com is loading properly.");
            r1.setScript("goto https://example.com\nassert-text h1 = Example Domain\nassert-exists a");
            r1.setIntervalMinutes(5);
            r1.setActive(true);
            ruleRepository.save(r1);

            TestRule r2 = new TestRule();
            r2.setName("Verify Google Homepage");
            r2.setDescription("Validates that Google search page loads and title is correct.");
            r2.setScript("goto https://www.google.com\nassert-title = Google");
            r2.setIntervalMinutes(10);
            r2.setActive(true);
            ruleRepository.save(r2);
            
            TestRule r3 = new TestRule();
            r3.setName("Failing Test Demonstration");
            r3.setDescription("A sample rule designed to fail to demonstrate screenshot capture and ticket creation.");
            r3.setScript("goto https://example.com\nassert-text h1 = This Will Fail Surely");
            r3.setIntervalMinutes(15);
            r3.setActive(false); // Keep inactive by default, but ready for manual test
            ruleRepository.save(r3);
        }
    }
}
