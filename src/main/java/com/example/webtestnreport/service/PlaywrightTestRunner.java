package com.example.webtestnreport.service;

import com.example.webtestnreport.model.TestRule;
import com.example.webtestnreport.model.TestRun;
import com.microsoft.playwright.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PlaywrightTestRunner {

    @Value("${app.screenshots.dir:./data/screenshots}")
    private String screenshotsDir;

    public TestRun runTest(TestRule rule) {
        TestRun run = new TestRun();
        run.setRuleId(rule.getId());
        run.setRuleName(rule.getName());
        run.setStartedAt(LocalDateTime.now());

        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("=== Test Started at ").append(run.getStartedAt()).append(" ===\n");
        logBuilder.append("Executing rule: ").append(rule.getName()).append("\n\n");

        long startTime = System.currentTimeMillis();
        String status = "SUCCESS";
        String errorMessage = null;
        String screenshotPath = null;

        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;
        Page page = null;

        try {
            playwright = Playwright.create();
            // Launch options (headless)
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(true);
            browser = playwright.chromium().launch(launchOptions);
            context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 720));
            page = context.newPage();

            String script = rule.getScript();
            if (script == null || script.trim().isEmpty()) {
                logBuilder.append("[WARNING] Script is empty. Nothing to execute.\n");
            } else {
                String[] lines = script.split("\\r?\\n");
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    int lineNumber = i + 1;

                    // Skip empty lines or comments
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                        continue;
                    }

                    logBuilder.append(String.format("[Line %d] Executing: %s\n", lineNumber, line));
                    executeCommandLine(page, line, logBuilder);
                }
            }
            logBuilder.append("\n=== Test completed successfully! ===\n");
        } catch (Throwable e) {
            status = "FAILED";
            errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
            logBuilder.append("\n[ERROR] Step failed: ").append(errorMessage).append("\n");

            // Attempt screenshot on failure
            if (page != null) {
                try {
                    File dir = new File(screenshotsDir);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    String fileName = "screenshot_" + rule.getId() + "_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
                    java.nio.file.Path path = Paths.get(screenshotsDir, fileName);
                    page.screenshot(new Page.ScreenshotOptions().setPath(path));
                    screenshotPath = "/api/runs/screenshot/" + fileName;
                    logBuilder.append("[INFO] Captured failure screenshot: ").append(fileName).append("\n");
                } catch (Exception ex) {
                    logBuilder.append("[WARNING] Failed to capture screenshot: ").append(ex.getMessage()).append("\n");
                }
            }
        } finally {
            if (context != null) {
                try { context.close(); } catch (Exception ignored) {}
            }
            if (browser != null) {
                try { browser.close(); } catch (Exception ignored) {}
            }
            if (playwright != null) {
                try { playwright.close(); } catch (Exception ignored) {}
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logBuilder.append("\nDuration: ").append(duration).append(" ms\n");

        run.setStatus(status);
        run.setDurationMs(duration);
        run.setLog(logBuilder.toString());
        run.setErrorMessage(errorMessage);
        run.setScreenshotPath(screenshotPath);

        return run;
    }

    private void executeCommandLine(Page page, String commandLine, StringBuilder logBuilder) {
        if (commandLine.startsWith("goto ")) {
            String url = commandLine.substring(5).trim();
            logBuilder.append("  -> Navigating to: ").append(url).append("\n");
            page.navigate(url);
            page.waitForLoadState();
        } else if (commandLine.startsWith("click ")) {
            String selector = commandLine.substring(6).trim();
            logBuilder.append("  -> Clicking selector: ").append(selector).append("\n");
            page.click(selector);
        } else if (commandLine.startsWith("fill ")) {
            String rest = commandLine.substring(5).trim();
            int eqIdx = rest.indexOf('=');
            if (eqIdx == -1) {
                throw new IllegalArgumentException("Invalid 'fill' syntax. Expected format: fill selector = value");
            }
            String selector = rest.substring(0, eqIdx).trim();
            String value = rest.substring(eqIdx + 1).trim();
            logBuilder.append("  -> Filling selector '").append(selector).append("' with: ").append(value).append("\n");
            page.fill(selector, value);
        } else if (commandLine.startsWith("type ")) {
            String rest = commandLine.substring(5).trim();
            int eqIdx = rest.indexOf('=');
            if (eqIdx == -1) {
                throw new IllegalArgumentException("Invalid 'type' syntax. Expected format: type selector = value");
            }
            String selector = rest.substring(0, eqIdx).trim();
            String value = rest.substring(eqIdx + 1).trim();
            logBuilder.append("  -> Typing into selector '").append(selector).append("' value: ").append(value).append("\n");
            page.type(selector, value);
        } else if (commandLine.startsWith("check ")) {
            String selector = commandLine.substring(6).trim();
            logBuilder.append("  -> Checking: ").append(selector).append("\n");
            page.check(selector);
        } else if (commandLine.startsWith("uncheck ")) {
            String selector = commandLine.substring(8).trim();
            logBuilder.append("  -> Unchecking: ").append(selector).append("\n");
            page.uncheck(selector);
        } else if (commandLine.startsWith("press ")) {
            String key = commandLine.substring(6).trim();
            logBuilder.append("  -> Pressing key: ").append(key).append("\n");
            page.keyboard().press(key);
        } else if (commandLine.startsWith("wait ")) {
            String msStr = commandLine.substring(5).trim();
            try {
                double ms = Double.parseDouble(msStr);
                logBuilder.append("  -> Waiting for ").append(ms).append(" ms\n");
                page.waitForTimeout(ms);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid wait time: " + msStr);
            }
        } else if (commandLine.startsWith("assert-text ")) {
            String rest = commandLine.substring(12).trim();
            int eqIdx = rest.indexOf('=');
            if (eqIdx == -1) {
                throw new IllegalArgumentException("Invalid 'assert-text' syntax. Expected format: assert-text selector = value");
            }
            String selector = rest.substring(0, eqIdx).trim();
            String expected = rest.substring(eqIdx + 1).trim();
            
            // Auto wait for selector to be visible before asserting
            page.locator(selector).first().waitFor();
            String actual = page.locator(selector).first().textContent();
            if (actual == null) actual = "";
            actual = actual.trim();
            logBuilder.append("  -> Asserting text in '").append(selector).append("'. Expected: '").append(expected).append("', Actual: '").append(actual).append("'\n");
            if (!actual.contains(expected)) {
                throw new AssertionError("Text assertion failed for selector '" + selector + "'. Expected to contain: '" + expected + "', but was: '" + actual + "'");
            }
        } else if (commandLine.startsWith("assert-title ")) {
            String rest = commandLine.substring(13).trim();
            int eqIdx = rest.indexOf('=');
            String expected = (eqIdx == -1) ? rest : rest.substring(eqIdx + 1).trim();
            String actual = page.title();
            if (actual == null) actual = "";
            actual = actual.trim();
            logBuilder.append("  -> Asserting title. Expected: '").append(expected).append("', Actual: '").append(actual).append("'\n");
            if (!actual.contains(expected)) {
                throw new AssertionError("Title assertion failed. Expected to contain: '" + expected + "', but was: '" + actual + "'");
            }
        } else if (commandLine.startsWith("assert-exists ")) {
            String selector = commandLine.substring(14).trim();
            logBuilder.append("  -> Asserting element exists: ").append(selector).append("\n");
            int count = page.locator(selector).count();
            if (count == 0) {
                throw new AssertionError("Assertion failed. Element not found for selector: '" + selector + "'");
            }
        } else if (commandLine.startsWith("assert-visible ")) {
            String selector = commandLine.substring(15).trim();
            logBuilder.append("  -> Asserting element visible: ").append(selector).append("\n");
            boolean visible = page.locator(selector).first().isVisible();
            if (!visible) {
                throw new AssertionError("Assertion failed. Element is not visible for selector: '" + selector + "'");
            }
        } else {
            throw new IllegalArgumentException("Unknown command: " + commandLine);
        }
    }
}
