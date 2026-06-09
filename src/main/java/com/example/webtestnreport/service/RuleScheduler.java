package com.example.webtestnreport.service;

import com.example.webtestnreport.model.TestRule;
import com.example.webtestnreport.model.TestRun;
import com.example.webtestnreport.repository.TestRuleRepository;
import com.example.webtestnreport.repository.TestRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class RuleScheduler {

    @Autowired
    private TestRuleRepository ruleRepository;

    @Autowired
    private TestRunRepository runRepository;

    @Autowired
    private PlaywrightTestRunner testRunner;

    @Autowired
    private TicketService ticketService;

    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        // Simple thread pool to run browser tests in parallel
        executorService = Executors.newFixedThreadPool(4);
    }

    @PreDestroy
    public void destroy() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    // Run scheduler check every 60 seconds
    @Scheduled(fixedRate = 60000)
    public void checkAndRunRules() {
        List<TestRule> activeRules = ruleRepository.findByActive(true);

        for (TestRule rule : activeRules) {
            // Get the latest run for this rule to see if it is due
            List<TestRun> runs = runRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
            boolean shouldRun = false;

            if (runs.isEmpty()) {
                shouldRun = true;
            } else {
                TestRun latestRun = runs.get(0);
                long minutesSinceLastRun = ChronoUnit.MINUTES.between(latestRun.getStartedAt(), LocalDateTime.now());
                if (minutesSinceLastRun >= rule.getIntervalMinutes()) {
                    shouldRun = true;
                }
            }

            if (shouldRun) {
                triggerRunAsync(rule);
            }
        }
    }

    public void triggerRunAsync(TestRule rule) {
        executorService.submit(() -> {
            try {
                TestRun run = testRunner.runTest(rule);
                runRepository.save(run);
                ticketService.processTestResult(run);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
