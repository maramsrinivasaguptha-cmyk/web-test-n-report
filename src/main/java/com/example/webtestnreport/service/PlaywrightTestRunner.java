package com.example.webtestnreport.service;

import com.example.webtestnreport.model.TestRule;
import com.example.webtestnreport.model.TestRun;
import com.microsoft.playwright.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

@Service
public class PlaywrightTestRunner {

    @Value("${app.screenshots.dir:./data/screenshots}")
    private String screenshotsDir;

    @Autowired
    private NoSqlMockDb noSqlMockDb;

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class TestStage {
        private String name;
        private String status = "PENDING"; // PENDING, RUNNING, SUCCESS, FAILED, SKIPPED
        private long durationMs = 0;
        private String error;

        public TestStage() {}

        public TestStage(String name) {
            this.name = name;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    private static class TestExecutionContext {
        Map<String, String> variables = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        
        String lastResponseBody;
        int lastResponseStatus;
        Map<String, List<String>> lastResponseHeaders = new HashMap<>();
        
        Connection dbConnection;
        List<Map<String, Object>> lastDbResult;
        int lastDbAffectedRows;
        
        List<Map<String, Object>> lastNoSqlResult;
        
        List<TestStage> stages = new ArrayList<>();
        TestStage currentStage = null;
        long stageStartTime = 0;
    }

    private static class PlaywrightSession {
        Playwright playwright;
        Browser browser;
        BrowserContext context;
        Page page;
    }

    private String getLocalPort() {
        String port = environment.getProperty("local.server.port");
        if (port == null) {
            port = environment.getProperty("server.port");
        }
        return port != null ? port : "8081"; // fallback to default
    }

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

        PlaywrightSession ps = new PlaywrightSession();
        TestExecutionContext context = new TestExecutionContext();

        try {
            String script = rule.getScript();
            if (script == null || script.trim().isEmpty()) {
                logBuilder.append("[WARNING] Script is empty. Nothing to execute.\n");
                TestStage defaultStage = new TestStage("Main Execution");
                defaultStage.setStatus("SUCCESS");
                context.stages.add(defaultStage);
            } else {
                String[] lines = script.split("\\r?\\n");
                
                // Pre-parse stages to populate the stage list in order
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                        continue;
                    }
                    if (line.startsWith("stage ")) {
                        String stageName = line.substring(6).trim();
                        context.stages.add(new TestStage(stageName));
                    }
                }
                
                // If no stages were declared, create a default stage
                if (context.stages.isEmpty()) {
                    context.stages.add(new TestStage("Main Execution"));
                }
                
                // Start the first stage
                context.currentStage = context.stages.get(0);
                context.currentStage.setStatus("RUNNING");
                context.stageStartTime = System.currentTimeMillis();
                logBuilder.append("=== Stage: ").append(context.currentStage.getName()).append(" ===\n");
                
                int currentStageIdx = 0;
                
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    int lineNumber = i + 1;

                    // Skip empty lines or comments
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                        continue;
                    }

                    // Handle stage transition
                    if (line.startsWith("stage ")) {
                        String stageName = line.substring(6).trim();
                        
                        // Complete previous stage
                        if (context.currentStage != null) {
                            context.currentStage.setStatus("SUCCESS");
                            context.currentStage.setDurationMs(System.currentTimeMillis() - context.stageStartTime);
                        }
                        
                        // Find next stage from our pre-parsed list
                        currentStageIdx++;
                        if (currentStageIdx < context.stages.size()) {
                            context.currentStage = context.stages.get(currentStageIdx);
                        } else {
                            // fallback
                            context.currentStage = new TestStage(stageName);
                            context.stages.add(context.currentStage);
                        }
                        context.currentStage.setStatus("RUNNING");
                        context.stageStartTime = System.currentTimeMillis();
                        logBuilder.append("\n=== Stage: ").append(context.currentStage.getName()).append(" ===\n");
                        continue;
                    }

                    // Resolve variables in this command line
                    String resolvedLine = resolveVariables(line, context.variables);
                    logBuilder.append(String.format("[Line %d] Executing: %s\n", lineNumber, resolvedLine));
                    
                    // Execute command line
                    executeCommandLine(ps, resolvedLine, context, logBuilder);
                }
                
                // Complete last stage
                if (context.currentStage != null && "RUNNING".equals(context.currentStage.getStatus())) {
                    context.currentStage.setStatus("SUCCESS");
                    context.currentStage.setDurationMs(System.currentTimeMillis() - context.stageStartTime);
                }
            }
            logBuilder.append("\n=== Test completed successfully! ===\n");
        } catch (Throwable e) {
            status = "FAILED";
            errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
            logBuilder.append("\n[ERROR] Step failed: ").append(errorMessage).append("\n");

            // Mark current stage as failed
            if (context.currentStage != null) {
                context.currentStage.setStatus("FAILED");
                context.currentStage.setError(errorMessage);
                context.currentStage.setDurationMs(System.currentTimeMillis() - context.stageStartTime);
            }
            
            // Mark all subsequent stages as SKIPPED
            boolean markSkipped = false;
            for (TestStage stage : context.stages) {
                if (markSkipped) {
                    stage.setStatus("SKIPPED");
                }
                if (stage == context.currentStage) {
                    markSkipped = true;
                }
            }

            // Attempt screenshot on failure
            if (ps.page != null) {
                try {
                    File dir = new File(screenshotsDir);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    String fileName = "screenshot_" + rule.getId() + "_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
                    java.nio.file.Path path = Paths.get(screenshotsDir, fileName);
                    ps.page.screenshot(new Page.ScreenshotOptions().setPath(path));
                    screenshotPath = "/api/runs/screenshot/" + fileName;
                    logBuilder.append("[INFO] Captured failure screenshot: ").append(fileName).append("\n");
                } catch (Exception ex) {
                    logBuilder.append("[WARNING] Failed to capture screenshot: ").append(ex.getMessage()).append("\n");
                }
            }
        } finally {
            if (ps.context != null) {
                try { ps.context.close(); } catch (Exception ignored) {}
            }
            if (ps.browser != null) {
                try { ps.browser.close(); } catch (Exception ignored) {}
            }
            if (ps.playwright != null) {
                try { ps.playwright.close(); } catch (Exception ignored) {}
            }
            if (context.dbConnection != null) {
                try { context.dbConnection.close(); } catch (Exception ignored) {}
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logBuilder.append("\nDuration: ").append(duration).append(" ms\n");

        run.setStatus(status);
        run.setDurationMs(duration);
        run.setLog(logBuilder.toString());
        run.setErrorMessage(errorMessage);
        run.setScreenshotPath(screenshotPath);
        
        // Serialize stages to stagesJson
        try {
            String stagesJson = objectMapper.writeValueAsString(context.stages);
            run.setStagesJson(stagesJson);
        } catch (Exception e) {
            logBuilder.append("[WARNING] Failed to serialize stages to JSON: ").append(e.getMessage()).append("\n");
        }

        return run;
    }

    private Page getOrInitPage(PlaywrightSession ps) {
        if (ps.page == null) {
            ps.playwright = Playwright.create();
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(true);
            ps.browser = ps.playwright.chromium().launch(launchOptions);
            ps.context = ps.browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 720));
            ps.page = ps.context.newPage();
        }
        return ps.page;
    }

    private boolean isBrowserCommand(String commandLine) {
        return commandLine.startsWith("goto ") ||
               commandLine.startsWith("click ") ||
               commandLine.startsWith("fill ") ||
               commandLine.startsWith("type ") ||
               commandLine.startsWith("check ") ||
               commandLine.startsWith("uncheck ") ||
               commandLine.startsWith("press ") ||
               commandLine.startsWith("wait ") ||
               commandLine.startsWith("assert-text ") ||
               commandLine.startsWith("assert-title ") ||
               commandLine.startsWith("assert-exists ") ||
               commandLine.startsWith("assert-visible ");
    }

    private void executeBrowserCommand(Page page, String commandLine, StringBuilder logBuilder) {
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
        }
    }

    private String resolveVariables(String line, Map<String, String> variables) {
        if (line == null) return null;
        
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        while (true) {
            int openIdx = line.indexOf("${", cursor);
            if (openIdx == -1) {
                result.append(line.substring(cursor));
                break;
            }
            result.append(line.substring(cursor, openIdx));
            int closeIdx = line.indexOf("}", openIdx);
            if (closeIdx == -1) {
                throw new IllegalArgumentException("Unclosed variable placeholder: " + line.substring(openIdx));
            }
            String varName = line.substring(openIdx + 2, closeIdx).trim();
            if (!variables.containsKey(varName)) {
                throw new IllegalArgumentException("Variable '" + varName + "' is not defined in this session context");
            }
            result.append(variables.get(varName));
            cursor = closeIdx + 1;
        }
        return result.toString();
    }

    private void executeHttp(String method, String url, String body, TestExecutionContext context, StringBuilder logBuilder) throws Exception {
        if (url.startsWith("/")) {
            url = "http://localhost:" + getLocalPort() + url;
        }
        logBuilder.append("  -> HTTP ").append(method).append(": ").append(url).append("\n");
        
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url));
        
        // Add headers
        for (Map.Entry<String, String> entry : context.headers.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        
        // Set body if POST/PUT
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            HttpRequest.BodyPublisher publisher = (body == null || body.isEmpty()) 
                    ? HttpRequest.BodyPublishers.noBody() 
                    : HttpRequest.BodyPublishers.ofString(body);
            builder.method(method, publisher);
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        
        context.lastResponseStatus = response.statusCode();
        context.lastResponseBody = response.body();
        context.lastResponseHeaders = response.headers().map();
        
        logBuilder.append("  -> Response Status: ").append(response.statusCode()).append("\n");
        if (response.body() != null && !response.body().isEmpty()) {
            String shortBody = response.body().length() > 500 ? response.body().substring(0, 500) + "..." : response.body();
            logBuilder.append("  -> Response Body: ").append(shortBody).append("\n");
        }
    }

    private String getJsonValueByPath(String json, String path) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        String[] segments = path.split("\\.");
        for (String segment : segments) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return null;
            }
            segment = segment.trim();
            if (segment.matches("\\d+")) {
                int index = Integer.parseInt(segment);
                node = node.get(index);
            } else {
                node = node.path(segment);
            }
        }
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private void executeDbQuery(String sql, TestExecutionContext context, StringBuilder logBuilder) throws Exception {
        if (context.dbConnection == null || context.dbConnection.isClosed()) {
            if (dataSource != null) {
                context.dbConnection = dataSource.getConnection();
                logBuilder.append("  -> Auto-connecting to default application datasource\n");
            } else {
                throw new IllegalStateException("No active SQL database connection. Call db-connect first.");
            }
        }
        logBuilder.append("  -> SQL Query: ").append(sql).append("\n");
        try (Statement stmt = context.dbConnection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    String colName = meta.getColumnLabel(i);
                    row.put(colName.toLowerCase(), rs.getObject(i));
                }
                rows.add(row);
            }
            context.lastDbResult = rows;
            logBuilder.append("  -> Query returned ").append(rows.size()).append(" rows\n");
            if (!rows.isEmpty()) {
                logBuilder.append("  -> First Row: ").append(rows.get(0)).append("\n");
            }
        }
    }

    private void executeDbUpdate(String sql, TestExecutionContext context, StringBuilder logBuilder) throws Exception {
        if (context.dbConnection == null || context.dbConnection.isClosed()) {
            if (dataSource != null) {
                context.dbConnection = dataSource.getConnection();
                logBuilder.append("  -> Auto-connecting to default application datasource\n");
            } else {
                throw new IllegalStateException("No active SQL database connection. Call db-connect first.");
            }
        }
        logBuilder.append("  -> SQL Execute: ").append(sql).append("\n");
        try (Statement stmt = context.dbConnection.createStatement()) {
            int affected = stmt.executeUpdate(sql);
            context.lastDbAffectedRows = affected;
            logBuilder.append("  -> Affected rows: ").append(affected).append("\n");
        }
    }

    private void executeCommandLine(PlaywrightSession ps, String commandLine, TestExecutionContext context, StringBuilder logBuilder) throws Throwable {
        if (isBrowserCommand(commandLine)) {
            Page page = getOrInitPage(ps);
            executeBrowserCommand(page, commandLine, logBuilder);
            return;
        }

        if (commandLine.startsWith("header ")) {
            String rest = commandLine.substring(7).trim();
            int eqIdx = rest.indexOf('=');
            if (eqIdx == -1) {
                throw new IllegalArgumentException("Invalid 'header' syntax. Expected: header Name = Value");
            }
            String name = rest.substring(0, eqIdx).trim();
            String value = rest.substring(eqIdx + 1).trim();
            context.headers.put(name, value);
            logBuilder.append("  -> Setting request header '").append(name).append("' = '").append(value).append("'\n");
        } else if (commandLine.startsWith("http-get ")) {
            String url = commandLine.substring(9).trim();
            executeHttp("GET", url, null, context, logBuilder);
        } else if (commandLine.startsWith("http-post ")) {
            String rest = commandLine.substring(10).trim();
            int eqIdx = rest.indexOf('=');
            String url = eqIdx == -1 ? rest : rest.substring(0, eqIdx).trim();
            String body = eqIdx == -1 ? "" : rest.substring(eqIdx + 1).trim();
            executeHttp("POST", url, body, context, logBuilder);
        } else if (commandLine.startsWith("http-put ")) {
            String rest = commandLine.substring(9).trim();
            int eqIdx = rest.indexOf('=');
            String url = eqIdx == -1 ? rest : rest.substring(0, eqIdx).trim();
            String body = eqIdx == -1 ? "" : rest.substring(eqIdx + 1).trim();
            executeHttp("PUT", url, body, context, logBuilder);
        } else if (commandLine.startsWith("http-delete ")) {
            String url = commandLine.substring(12).trim();
            executeHttp("DELETE", url, null, context, logBuilder);
        } else if (commandLine.startsWith("assert-status ")) {
            String rest = commandLine.substring(14).trim();
            int eqIdx = rest.indexOf('=');
            String valStr = eqIdx == -1 ? rest : rest.substring(eqIdx + 1).trim();
            int expected = Integer.parseInt(valStr);
            logBuilder.append("  -> Asserting HTTP status. Expected: ").append(expected).append(", Actual: ").append(context.lastResponseStatus).append("\n");
            if (context.lastResponseStatus != expected) {
                throw new AssertionError("HTTP Status assertion failed. Expected: " + expected + ", but was: " + context.lastResponseStatus);
            }
        } else if (commandLine.startsWith("assert-json ")) {
            String rest = commandLine.substring(12).trim();
            int eqIdx = rest.indexOf('=');
            if (eqIdx == -1) {
                throw new IllegalArgumentException("Invalid 'assert-json' syntax. Expected: assert-json path = value");
            }
            String path = rest.substring(0, eqIdx).trim();
            String expected = rest.substring(eqIdx + 1).trim();
            
            if (context.lastResponseBody == null) {
                throw new AssertionError("No HTTP response body found to assert-json on.");
            }
            String actual = getJsonValueByPath(context.lastResponseBody, path);
            logBuilder.append("  -> Asserting JSON path '").append(path).append("'. Expected: '").append(expected).append("', Actual: '").append(actual).append("'\n");
            if (actual == null || !actual.equals(expected)) {
                throw new AssertionError("JSON path '" + path + "' assertion failed. Expected: '" + expected + "', but was: '" + actual + "'");
            }
        } else if (commandLine.startsWith("store-json ")) {
            String rest = commandLine.substring(11).trim();
            int eqIdx = rest.indexOf('=');
            if (eqIdx == -1) {
                throw new IllegalArgumentException("Invalid 'store-json' syntax. Expected: store-json path = varName");
            }
            String path = rest.substring(0, eqIdx).trim();
            String varName = rest.substring(eqIdx + 1).trim();
            
            if (context.lastResponseBody == null) {
                throw new AssertionError("No HTTP response body found to store value from.");
            }
            String val = getJsonValueByPath(context.lastResponseBody, path);
            if (val == null) {
                throw new AssertionError("JSON path '" + path + "' returned null or not found in body: " + context.lastResponseBody);
            }
            context.variables.put(varName, val);
            logBuilder.append("  -> Stored JSON path '").append(path).append("' value '").append(val).append("' into variable: ").append(varName).append("\n");
        } else if (commandLine.startsWith("store-header ")) {
            String rest = commandLine.substring(13).trim();
            int eqIdx = rest.indexOf('=');
            if (eqIdx == -1) {
                throw new IllegalArgumentException("Invalid 'store-header' syntax. Expected: store-header headerName = varName");
            }
            String headerName = rest.substring(0, eqIdx).trim();
            String varName = rest.substring(eqIdx + 1).trim();
            
            List<String> values = context.lastResponseHeaders.get(headerName.toLowerCase());
            if (values == null || values.isEmpty()) {
                throw new AssertionError("Header '" + headerName + "' not found in response.");
            }
            String val = values.get(0);
            context.variables.put(varName, val);
            logBuilder.append("  -> Stored response header '").append(headerName).append("' value '").append(val).append("' into variable: ").append(varName).append("\n");
        }

        else if (commandLine.startsWith("db-connect")) {
            String rest = commandLine.substring(10).trim();
            if (context.dbConnection != null && !context.dbConnection.isClosed()) {
                context.dbConnection.close();
            }
            
            if (rest.isEmpty() || "default".equalsIgnoreCase(rest)) {
                if (dataSource != null) {
                    context.dbConnection = dataSource.getConnection();
                    logBuilder.append("  -> Connected to default application datasource\n");
                } else {
                    throw new IllegalStateException("Default application datasource is not configured/available.");
                }
            } else {
                int eqIdx = rest.indexOf('=');
                String jdbcUrl = eqIdx == -1 ? rest : rest.substring(0, eqIdx).trim();
                String user = null;
                String pass = null;
                if (eqIdx != -1) {
                    String creds = rest.substring(eqIdx + 1).trim();
                    int pipeIdx = creds.indexOf('|');
                    if (pipeIdx != -1) {
                        user = creds.substring(0, pipeIdx).trim();
                        pass = creds.substring(pipeIdx + 1).trim();
                    } else {
                        user = creds;
                    }
                }
                logBuilder.append("  -> Connecting to JDBC URL: ").append(jdbcUrl).append("\n");
                context.dbConnection = DriverManager.getConnection(jdbcUrl, user, pass);
            }
        } else if (commandLine.startsWith("db-query ")) {
            String sql = commandLine.substring(9).trim();
            executeDbQuery(sql, context, logBuilder);
        } else if (commandLine.startsWith("db-execute ")) {
            String sql = commandLine.substring(11).trim();
            executeDbUpdate(sql, context, logBuilder);
        } else if (commandLine.startsWith("assert-db ")) {
            String rest = commandLine.substring(10).trim();
            int eqIdx = rest.indexOf('=');
            if (eqIdx == -1) {
                throw new IllegalArgumentException("Invalid 'assert-db' syntax. Expected: assert-db column = expectedValue");
            }
            String colName = rest.substring(0, eqIdx).trim().toLowerCase();
            String expected = rest.substring(eqIdx + 1).trim();
            
            if (context.lastDbResult == null || context.lastDbResult.isEmpty()) {
                throw new AssertionError("No database query results found to assert-db on.");
            }
            Map<String, Object> firstRow = context.lastDbResult.get(0);
            Object actualObj = firstRow.get(colName);
            String actual = actualObj != null ? actualObj.toString() : null;
            
            logBuilder.append("  -> Asserting DB column '").append(colName).append("'. Expected: '").append(expected).append("', Actual: '").append(actual).append("'\n");
            if (actual == null || !actual.equals(expected)) {
                throw new AssertionError("DB Column '" + colName + "' assertion failed. Expected: '" + expected + "', but was: '" + actual + "'");
            }
        } else if (commandLine.startsWith("assert-db-rows ")) {
            String rest = commandLine.substring(15).trim();
            int eqIdx = rest.indexOf('=');
            String valStr = eqIdx == -1 ? rest : rest.substring(eqIdx + 1).trim();
            int expected = Integer.parseInt(valStr);
            
            int actual = context.lastDbResult != null ? context.lastDbResult.size() : 0;
            logBuilder.append("  -> Asserting DB Row Count. Expected: ").append(expected).append(", Actual: ").append(actual).append("\n");
            if (actual != expected) {
                throw new AssertionError("DB Row Count assertion failed. Expected: " + expected + ", but was: " + actual);
            }
        } else if (commandLine.startsWith("store-db ")) {
            String rest = commandLine.substring(9).trim();
            int eqIdx = rest.indexOf('=');
            if (eqIdx == -1) {
                throw new IllegalArgumentException("Invalid 'store-db' syntax. Expected: store-db column = varName");
            }
            String colName = rest.substring(0, eqIdx).trim().toLowerCase();
            String varName = rest.substring(eqIdx + 1).trim();
            
            if (context.lastDbResult == null || context.lastDbResult.isEmpty()) {
                throw new AssertionError("No database query results found to store value from.");
            }
            Map<String, Object> firstRow = context.lastDbResult.get(0);
            Object valObj = firstRow.get(colName);
            if (valObj == null) {
                throw new AssertionError("DB Column '" + colName + "' returned null or not found in result first row.");
            }
            String val = valObj.toString();
            context.variables.put(varName, val);
            logBuilder.append("  -> Stored DB column '").append(colName).append("' value '").append(val).append("' into variable: ").append(varName).append("\n");
        }

        else if (commandLine.startsWith("nosql-insert ")) {
            String rest = commandLine.substring(13).trim();
            int eqIdx = rest.indexOf('=');
            if (eqIdx == -1) {
                throw new IllegalArgumentException("Invalid 'nosql-insert' syntax. Expected: nosql-insert collection = json");
            }
            String collection = rest.substring(0, eqIdx).trim();
            String json = rest.substring(eqIdx + 1).trim();
            
            logBuilder.append("  -> Inserting into NoSQL collection '").append(collection).append("'\n");
            Map<String, Object> inserted = noSqlMockDb.insert(collection, json);
            logBuilder.append("  -> Document Inserted: ").append(inserted).append("\n");
        } else if (commandLine.startsWith("nosql-find ")) {
            String rest = commandLine.substring(11).trim();
            int eqIdx = rest.indexOf('=');
            if (eqIdx == -1) {
                throw new IllegalArgumentException("Invalid 'nosql-find' syntax. Expected: nosql-find collection = queryJson");
            }
            String collection = rest.substring(0, eqIdx).trim();
            String queryJson = rest.substring(eqIdx + 1).trim();
            
            logBuilder.append("  -> Finding in NoSQL collection '").append(collection).append("' with query: ").append(queryJson).append("\n");
            List<Map<String, Object>> docs = noSqlMockDb.find(collection, queryJson);
            context.lastNoSqlResult = docs;
            logBuilder.append("  -> Found ").append(docs.size()).append(" matching documents\n");
            if (!docs.isEmpty()) {
                logBuilder.append("  -> First Found: ").append(docs.get(0)).append("\n");
            }
        } else if (commandLine.startsWith("assert-nosql ")) {
            String rest = commandLine.substring(13).trim();
            int eqIdx = rest.indexOf('=');
            if (eqIdx == -1) {
                throw new IllegalArgumentException("Invalid 'assert-nosql' syntax. Expected: assert-nosql field = expectedValue");
            }
            String field = rest.substring(0, eqIdx).trim();
            String expected = rest.substring(eqIdx + 1).trim();
            
            String actual = getNoSqlValue(context.lastNoSqlResult, field);
            logBuilder.append("  -> Asserting NoSQL field '").append(field).append("'. Expected: '").append(expected).append("', Actual: '").append(actual).append("'\n");
            if (actual == null || !actual.equals(expected)) {
                throw new AssertionError("NoSQL field '" + field + "' assertion failed. Expected: '" + expected + "', but was: '" + actual + "'");
            }
        } else if (commandLine.startsWith("store-nosql ")) {
            String rest = commandLine.substring(12).trim();
            int eqIdx = rest.indexOf('=');
            if (eqIdx == -1) {
                throw new IllegalArgumentException("Invalid 'store-nosql' syntax. Expected: store-nosql field = varName");
            }
            String field = rest.substring(0, eqIdx).trim();
            String varName = rest.substring(eqIdx + 1).trim();
            
            String val = getNoSqlValue(context.lastNoSqlResult, field);
            if (val == null) {
                throw new AssertionError("NoSQL field '" + field + "' returned null or not found in first result document.");
            }
            context.variables.put(varName, val);
            logBuilder.append("  -> Stored NoSQL field '").append(field).append("' value '").append(val).append("' into variable: ").append(varName).append("\n");
        } else {
            throw new IllegalArgumentException("Unknown command: " + commandLine);
        }
    }

    private String getNoSqlValue(List<Map<String, Object>> lastResult, String field) {
        if (lastResult == null || lastResult.isEmpty()) {
            throw new AssertionError("No NoSQL search results found to assert/store");
        }
        Map<String, Object> firstDoc = lastResult.get(0);
        String[] parts = field.split("\\.");
        Object current = firstDoc;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current != null ? current.toString() : null;
    }
}
