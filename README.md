# WebTestNReport 🚀

**WebTestNReport** is a lightweight, high-performance Web UI QA Testing & Incident Tracking platform. Built on Spring Boot and Playwright, it enables users to write automated browser tests using a simple line-by-line **Scratchpad DSL (Domain Specific Language)**, run them on schedule or on-demand, capture screenshots on failure, and track failure tickets on an interactive Kanban board.

---

## 🌟 Key Features

1. **Scratchpad DSL Engine**: Write clear, line-by-line browser automation scripts (e.g., `goto`, `click`, `fill`, `assert-text`).
2. **Headless Browser Execution**: Runs Playwright Chromium context under the hood for maximum execution speed and reliability.
3. **Automated Ticket Lifecycle**: Creates high-priority tickets automatically on test failure, updates tickets if failure persists, and auto-resolves tickets when subsequent runs pass.
4. **Persistent Datastore**: Stores rules, runs, and incident tickets in a file-based H2 database (`./data/webtestdb`).
5. **Interactive Glassmorphic SPA**: Modern dark slate dashboard featuring live metrics, a visual Kanban ticket board, a script editor with insertion helpers, and a live log runner.
6. **Background Scheduling**: Runs tests periodically at user-defined minute intervals.

---

## 🛠️ Technology Stack

- **Backend Framework**: Spring Boot 3 (Java 17)
- **Database / JPA**: Spring Data JPA with H2 Database
- **Automation Library**: Microsoft Playwright Java
- **Frontend SPA**: HTML5, Vanilla CSS3 (Slate/Glassmorphism theme), Vanilla ES6 JavaScript (No bulky framework)
- **Icons & Fonts**: FontAwesome 6, Google Fonts (Outfit, Inter, JetBrains Mono)

---

## 📂 Project Architecture & Code Directory

The core files and components of the codebase are mapped below:

* **Build & Configuration**:
  * [pom.xml](pom.xml) — Maven configuration with dependencies for Spring Boot Web, JPA, H2, and Playwright.
  * [application.properties](src/main/resources/application.properties) — Database, screenshots directory, and H2 settings.

* **Database Entities & Repositories**:
  * [TestRule](src/main/java/com/example/webtestnreport/model/TestRule.java) — Model for testing rules containing custom scripts and interval properties.
  * [TestRun](src/main/java/com/example/webtestnreport/model/TestRun.java) — Log record containing execution status, error messages, and screenshot paths.
  * [Ticket](src/main/java/com/example/webtestnreport/model/Ticket.java) — Ticket entity storing failure status (Open, In Progress, Resolved) and severity.

* **Services & Business Logic**:
  * [PlaywrightTestRunner](src/main/java/com/example/webtestnreport/service/PlaywrightTestRunner.java) — Interprets Scratchpad scripts line-by-line using Playwright, capturing failure screenshots.
  * [RuleScheduler](src/main/java/com/example/webtestnreport/service/RuleScheduler.java) — Manages scheduled background rule checks and parallel executions.
  * [TicketService](src/main/java/com/example/webtestnreport/service/TicketService.java) — Automates ticket creation, updates, and automatic resolution rules.
  * [DataInitializer](src/main/java/com/example/webtestnreport/service/DataInitializer.java) — Initializes default test rules on application startup.

* **REST API Endpoints**:
  * [TestRuleController](src/main/java/com/example/webtestnreport/controller/TestRuleController.java) — Endpoints for managing and running rules.
  * [TestRunController](src/main/java/com/example/webtestnreport/controller/TestRunController.java) — Endpoints to retrieve run history and serve failure screenshots.
  * [TicketController](src/main/java/com/example/webtestnreport/controller/TicketController.java) — Endpoints to fetch tickets and transition status/severity.

* **Frontend Dashboard**:
  * [index.html](src/main/resources/static/index.html) — Core SPA structural layout.
  * [styles.css](src/main/resources/static/css/styles.css) — Premium CSS layout styles.
  * [app.js](src/main/resources/static/js/app.js) — Handles API requests, chart rendering, terminal logs, and modals.

---

## ⚡ Getting Started

### Prerequisites
- Java 17 or higher
- Maven (or use the packaged wrapper `mvnw`)

### Build and Run

1. **Install Playwright Browsers & Build Project**:
   Run Maven package to download libraries and compile the project:
   ```bash
   mvn clean package
   ```

2. **Launch the Spring Boot Server**:
   Start the application:
   ```bash
   mvn spring-boot:run
   ```
   *The application starts at: http://localhost:8081*

3. **Accessing H2 Console**:
   Browse the database at http://localhost:8081/h2-console
   - **JDBC URL**: `jdbc:h2:file:./data/webtestdb`
   - **Username**: `sa`
   - **Password**: `password`

---

## 📡 REST API Reference

| Endpoint | Method | Description |
|---|---|---|
| `/api/rules` | `GET` | Retrieve list of all defined test rules |
| `/api/rules` | `POST` | Create a new test rule |
| `/api/rules/{id}` | `PUT` | Update an existing rule description, script, or schedule interval |
| `/api/rules/{id}` | `DELETE` | Delete a test rule |
| `/api/rules/{id}/run` | `POST` | Synchronously run a rule's script using Playwright and return the run result |
| `/api/runs` | `GET` | Get recent runs ordered by start time |
| `/api/runs/rule/{ruleId}` | `GET` | Get runs history for a specific rule |
| `/api/runs/screenshot/{fileName}` | `GET` | Fetch failure screenshot image resource |
| `/api/tickets` | `GET` | Get all incident tickets ordered by creation date |
| `/api/tickets/{id}/status` | `PUT` | Update ticket status (`OPEN`, `IN_PROGRESS`, `RESOLVED`) |
| `/api/tickets/{id}/severity` | `PUT` | Update ticket severity (`LOW`, `MEDIUM`, `HIGH`) |

---

## 📖 User Guide

For detailed information on writing automation scripts, customizing element selectors, configuring database backups, and resolving tickets, refer to the full [USER_GUIDE.md](USER_GUIDE.md).
