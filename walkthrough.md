# Walkthrough - Web UI Testing & Ticket Creator

We have successfully built and verified a lightweight, high-performance Web UI QA Testing application called **WebTestNReport** inside a Spring Boot framework. It utilizes a custom "Scratchpad DSL" to describe actions, runs them using a headless Playwright Chromium instance, and automatically manages tickets on test failures.

## Features Built

1. **Scratchpad DSL Engine**:
   - Parses simple line-by-line actions: `goto <url>`, `click <selector>`, `fill <selector> = <value>`, `type <selector> = <value>`, `press <key>`, `wait <ms>`, `check <selector>`, `uncheck <selector>`.
   - Supports rich assertions: `assert-text <selector> = <expected>`, `assert-title = <expected>`, `assert-exists <selector>`, `assert-visible <selector>`.
   - Runs headless Playwright. In the event of failure (assertion or page load crash), it stops instantly, captures a screenshot, compiles the log, and creates/updates an incident ticket.

2. **Spring Boot Backend**:
   - JPA entities (`TestRule`, `TestRun`, `Ticket`) stored in a persistent H2 database file (`./data/webtestdb`).
   - `RuleScheduler`: A background worker that runs active tests periodically based on their custom minute interval.
   - `TicketService`: Auto-creates high-priority tickets on rule failure, auto-resolves tickets on subsequent test success.
   - REST Controllers exposing simple APIs.

3. **Interactive Frontend SPA**:
   - Dark Slate Glassmorphic theme using Google Fonts Outfit and Inter.
   - **Dashboard**: Live success rate pie chart, metrics, and list of active incidents.
   - **Test Rules Builder**: Interactive rule card list. Users can add, update, delete, or trigger runs.
   - **Scratchpad Code Editor**: Text editor with line numbering and a click-to-insert snippet dropdown (making it easy to build actions).
   - **Live Terminal Log**: Manual test triggers stream logs to an overlay console box.
   - **Tickets Kanban Board**: Visual Columns (Open, In Progress, Resolved) with one-click status transitions.
   - **Execution Details Viewer**: Displays historical logs and side-by-side failure screenshots.
   - **Guide & About Tab**: Full in-UI reference manual explaining all Scratchpad commands (goto, click, fill, type, wait, assert-text, etc.) with coding examples.

## Dropdown & Styling Bugfix
We resolved a CSS clipping bug where the bottom choices of the **Insert Snippet** dropdown (e.g. `assert-visible`, `wait 2000`) were cut off due to `overflow: hidden` on the `.scratchpad-section` container. We updated the container's overflow to `visible`, increased the dropdown's `z-index` to 50, and explicitly added border-radius matching to inner header and footer elements.

## Verification Details

We successfully executed two automated browser subagent passes to verify the application:
1. **Pass 1**: Navigated to dashboard, triggered a manual rule run (demonstrating browser download logs, screenshot capture, H2 persistence, and open ticket creation on the Kanban board).
2. **Pass 2**: Opened the "Create New Rule" modal, clicked the "Insert Snippet" dropdown, and verified that all commands (including the last options) float properly over the editor and are fully visible. Switched to the new **Guide & About** tab and verified that all documentation sections load correctly.

### Browser Verification Recording
Here is the recorded session showing the updated UI, snippet dropdown visibility, and Guide tab layout:

![Verification Flow](C:/Users/DELL/.gemini/antigravity-ide/brain/546b8dbc-3aa9-43f8-ad89-94a4d5a26cf6/verify_guide_n_dropdown_1781030873353.webp)

