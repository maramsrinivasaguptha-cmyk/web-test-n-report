# Implementation Plan - Web UI Testing & Ticket Creator

We will create a lightweight Spring Boot application called **WebTestNReport** that allows users to create web UI test rules using a simple "scratchpad" script, runs them using a headless Playwright browser, and automatically generates/manages tickets on test failures.

## User Review Required

> [!NOTE]
> We will use **Playwright Java** for browser automation. Playwright is modern, runs headless, requires no separate driver installation (automatically downloads chromium), and is extremely fast and robust compared to legacy Selenium setups.

> [!IMPORTANT]
> The test scripts are written in a simple, easy-to-read "Scratchpad DSL". Users can write plain-text instructions (e.g. `goto URL`, `click selector`, `fill selector = value`, `assert-text selector = value`). The backend parses and executes these actions line-by-line.

## Proposed Changes

We will create a new Spring Boot maven structure in our workspace `c:\Users\DELL\work\WebTestNReport`.

### 1. Build and Configuration

#### [NEW] [pom.xml](file:///c:/Users/DELL/work/WebTestNReport/pom.xml)
Configure Maven dependencies:
- Spring Boot Starter Web, JPA, H2 Database.
- Playwright Java dependency for browser automation.
- Spring Boot DevTools for rapid reloading.

#### [NEW] [application.properties](file:///c:/Users/DELL/work/WebTestNReport/src/main/resources/application.properties)
Configure the database (H2 in-memory/file), port (e.g. `8080`), and screenshot directory.

---

### 2. Data Models (JPA Entities)

#### [NEW] [TestRule.java](file:///c:/Users/DELL/work/WebTestNReport/src/main/java/com/example/webtestnreport/model/TestRule.java)
Fields: `id`, `name`, `description`, `script`, `cronExpression` (or run interval in minutes), `active`, `createdAt`, `updatedAt`.

#### [NEW] [TestRun.java](file:///c:/Users/DELL/work/WebTestNReport/src/main/java/com/example/webtestnreport/model/TestRun.java)
Fields: `id`, `ruleId`, `ruleName`, `status` (SUCCESS/FAILED), `startedAt`, `durationMs`, `log` (step-by-step logs), `errorMessage`, `screenshotPath`.

#### [NEW] [Ticket.java](file:///c:/Users/DELL/work/WebTestNReport/src/main/java/com/example/webtestnreport/model/Ticket.java)
Fields: `id`, `ruleId`, `ruleName`, `testRunId`, `title`, `description`, `status` (OPEN, IN_PROGRESS, RESOLVED), `severity` (HIGH, MEDIUM, LOW), `createdAt`, `updatedAt`.

---

### 3. Backend Logic & Services

#### [NEW] [PlaywrightTestRunner.java](file:///c:/Users/DELL/work/WebTestNReport/src/main/java/com/example/webtestnreport/service/PlaywrightTestRunner.java)
The core runner that:
1. Receives a `TestRule` and spins up a headless Playwright Chromium page.
2. Parses the rule's scratchpad script line-by-line.
3. Executes commands (`goto`, `click`, `fill`, `assert-text`, `assert-title`, `wait`).
4. Logs each action.
5. In case of an assertion failure or exception: captures a screenshot, stops execution, and logs the error.
6. Returns a detailed `TestRun` report.

#### [NEW] [RuleScheduler.java](file:///c:/Users/DELL/work/WebTestNReport/src/main/java/com/example/webtestnreport/service/RuleScheduler.java)
A scheduled service that periodically fetches active rules and runs them in the background. If a rule run fails, it automatically calls a ticket service to create or update an open ticket.

#### [NEW] [TicketService.java](file:///c:/Users/DELL/work/WebTestNReport/src/main/java/com/example/webtestnreport/service/TicketService.java)
Handles ticket lifecycle:
- On failure: Creates a new ticket if none exists for the rule. If a ticket is already open, links the new failure.
- On success: Auto-flags the ticket if it was previously failing, or notifies that it passed.

---

### 4. REST Controllers

#### [NEW] [TestRuleController.java](file:///c:/Users/DELL/work/WebTestNReport/src/main/java/com/example/webtestnreport/controller/TestRuleController.java)
CRUD endpoints for managing rules and triggering dynamic run events.

#### [NEW] [TestRunController.java](file:///c:/Users/DELL/work/WebTestNReport/src/main/java/com/example/webtestnreport/controller/TestRunController.java)
Endpoints to fetch logs and serve screenshot images.

#### [NEW] [TicketController.java](file:///c:/Users/DELL/work/WebTestNReport/src/main/java/com/example/webtestnreport/controller/TicketController.java)
Endpoints to fetch tickets and update ticket status.

---

### 5. Frontend UI (Static Resources)

#### [NEW] [index.html](file:///c:/Users/DELL/work/WebTestNReport/src/main/resources/static/index.html)
A single-page application structure featuring:
- **Dashboard View**: High-level statistics (success rates, total tickets, rule status) and run logs.
- **Rules Manager**: Table of rules, dynamic modal/form to create/update rules, and a **Scratchpad Editor** with quick snippets to insert actions.
- **Runs Log**: History of all test execution runs, status, duration, logs, and screenshots.
- **Tickets Dashboard**: Kanban-style cards showing open/in-progress tickets with quick buttons to update their status.

#### [NEW] [styles.css](file:///c:/Users/DELL/work/WebTestNReport/src/main/resources/static/css/styles.css)
Custom premium styling using HSL color variables, smooth transitions, card layouts with backdrop filters, and clear badges for statuses.

#### [NEW] [app.js](file:///c:/Users/DELL/work/WebTestNReport/src/main/resources/static/js/app.js)
Frontend logic that fetches data from backend APIs, handles rule updates, runs tests dynamically, and renders execution logs in real-time.

---

## Verification Plan

### Automated/Build Verification
- Run `./mvnw.cmd clean compile` to ensure all Java and Playwright dependencies build correctly.
- Implement a simple integration test verifying the parser logic.

### Manual Verification
- Start the app using `./mvnw.cmd spring-boot:run`.
- Open `http://localhost:8080` in the browser.
- Create a test rule targeting a local/public URL (e.g. `https://example.com`) and write steps to assert its header.
- Intentionally modify the rule to fail (e.g. assert text that does not exist) and verify that a Ticket is created and screenshot is captured.
- Resolve the ticket and verify status transitions.
