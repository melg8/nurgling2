<!--
SYNC IMPACT REPORT
==================
Version change: 1.2.2 → 2.0.0 (MAJOR: AI-first development model)

Principles Modified:
  - IV. Verification Through Testing → IV. Automatic Testing First (stricter)
  - VII. Mandatory Self-Review → VII. AI-Assisted Development & Feedback

Principles Added:
  - None

Added Sections:
  - AI Feedback Mechanisms (in Development Workflow)

Removed Sections:
  - None

Templates Status:
  - plan-template.md: ✅ aligned
  - spec-template.md: ✅ aligned
  - tasks-template.md: ✅ aligned

Follow-up TODOs:
  - TODO(LOG_FORMAT): Define structured logging format specification
  - TODO(COVERAGE_TARGETS): Set specific code coverage percentage targets
  - TODO(AI_FEEDBACK_FORMAT): Define standard format for AI feedback files
-->

# Nurgling2 Constitution

## Core Principles

### I. Code Quality First

All code MUST meet high quality standards before merging. Quality is non-negotiable
even when under time pressure.

**Non-Negotiable Rules:**
- Code MUST compile without warnings with strict compiler flags enabled
- All public APIs MUST have Javadoc documentation
- Methods MUST NOT exceed 50 lines; classes MUST NOT exceed 500 lines (exceptions
  require explicit justification)
- Cyclomatic complexity MUST NOT exceed 15 per method
- No raw types, unchecked casts, or deprecated API usage without justification
- All resources MUST be properly closed (try-with-resources mandatory)

**Rationale:** MMO game clients handle complex state and concurrent operations.
Poor quality code leads to memory leaks, race conditions, and unpredictable
behavior that frustrates users and is difficult to debug.

---

### II. Incremental Refactoring (Boy Scout Rule)

Legacy code MUST be improved incrementally. Every change leaves the codebase
cleaner than it was found.

**Non-Negotiable Rules:**
- When touching a file, refactor at least one code smell (long method, duplicate
  code, poor naming)
- Big refactoring MUST be broken into small, reviewable commits (<400 lines)
- Refactoring MUST NOT change behavior without explicit user approval
- Each refactoring step MUST maintain passing tests
- Document refactoring decisions in commit messages

**Rationale:** Large legacy codebases cannot be rewritten in one pass. Incremental
improvement reduces risk, allows continuous delivery, and builds team knowledge
gradually.

---

### III. Comprehensive Logging

All significant operations MUST produce structured log output for debugging,
AI assistance, and user feedback.

**Non-Negotiable Rules:**
- Use SLF4J with Logback (or project-standard logging framework)
- Log levels MUST follow: ERROR (failures), WARN (recoverable issues),
  INFO (user-visible actions), DEBUG (detailed flow), TRACE (verbose)
- All bot/automation actions MUST log at INFO level with context
- Exceptions MUST be logged with full stack traces at ERROR level
- Logs MUST include: timestamp, thread, class, method, contextual data
- Sensitive data (credentials, tokens) MUST NEVER be logged

**Rationale:** Logs are the primary debugging tool for both developers and AI
assistants. Comprehensive logging enables rapid issue diagnosis and allows
users to understand what the client is doing.

---

### IV. Automatic Testing First

All code MUST be automatically tested. Manual testing is a last resort only.
The goal is to minimize human testing time by maximizing automatic verification.

**Non-Negotiable Rules:**
- Every feature/fix MUST have automatic tests before commit
- If it CAN be tested automatically, it MUST be tested automatically
- Unit tests MUST use JUnit 5 with Mockito for mocking
- Integration tests MUST exist for game server communication
- Test coverage MUST NOT decrease (target: >80% for new code)
- For code improvements/refactoring: semi-automatic tests MUST be created
  (e.g., visual comparison, behavior capture/replay, snapshot tests)
- Semi-automatic tests MUST provide clear pass/fail feedback to AI
- Flaky tests are forbidden and must be fixed immediately
- Tests MUST be independent, repeatable, and fast (<100ms per unit test)
- When automatic testing is truly impossible: document why + manual test steps

**Rationale:** AI writes most code. Automatic tests provide immediate feedback
on hallucinations and misunderstandings. This reduces time-consuming manual
testing by humans and enables rapid iteration with AI assistance.

---

### V. Modular Architecture

Code MUST be organized into cohesive, loosely-coupled modules with clear
responsibilities.

**Non-Negotiable Rules:**
- Each package MUST have a single, well-defined responsibility
- Cross-package dependencies MUST be acyclic (no circular dependencies)
- Game mechanics, UI, and networking MUST be in separate packages
- Use dependency injection for testability (constructor injection preferred)
- Interfaces MUST define contracts between modules
- Avoid static state; use instance fields with clear ownership

**Rationale:** Modular architecture enables parallel development, simplifies
testing, and makes the codebase approachable for new contributors. Clear
boundaries reduce cognitive load when navigating legacy code.

---

### VI. Documentation & Knowledge Sharing

Knowledge MUST be documented and accessible. Future developers (and AI
assistants) must understand why decisions were made.

**Non-Negotiable Rules:**
- README.md MUST describe build process, run configuration, and architecture
- Complex algorithms MUST have inline comments explaining the "why"
- Architecture decisions MUST be recorded in `docs/decisions/`
- Bot configurations and automation rules MUST have user documentation
- API contracts with game server MUST be documented in `docs/protocol/`

**Rationale:** MMO clients encode game mechanics that are not obvious from
code alone. Documentation preserves institutional knowledge and enables AI
assistants to provide better support.

---

### VII. AI-Assisted Development & Feedback

This project is developed primarily with AI assistance (Qwen Code, spec-kit).
All workflows MUST optimize for AI-human collaboration and efficient feedback.

**Non-Negotiable Rules:**
- When AI makes mistakes, it should create feedback artifacts to prevent recurrence:
  - Failing tests that capture the hallucination
  - Documentation of what AI misunderstood
  - Additional logs for AI to gain knowledge
- When AI repeatedly fails at a task: break it into smaller steps

**Rationale:** AI is the primary coder. Efficient feedback loops reduce
hallucinations and improve code quality. Persistent feedback artifacts
help AI learn from mistakes and prevent repeating them.

---

## Code Quality Standards

**Build Configuration:**
- Java 21 or higher (LTS version)
- Ant build system (project standard: Ant based on build.xml)
- Static analysis: SpotBugs, PMD, or Checkstyle enforced in CI

**Code Style:**
- Follow Google Java Style Guide or project-established formatter
- 4-space indentation, no tabs
- Line length limit: 120 characters
- Braces required for all control structures

---

## Development Workflow

**Commit Standards:**
- Conventional Commits format: `type(scope): description`
- Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`
- Commits MUST be atomic (single logical change)
- Commit messages MUST explain why, not what

**Refactoring Process:**
1. Identify code smell or improvement opportunity
2. Start working on a focused refactoring
3. Write tests for current behavior (if missing)
4. Make small, incremental changes
5. Verify tests pass after each change
6. Commit with clear message explaining the refactoring

**Self-Review Checklist (Mandatory Before Commit):**
- [ ] Reviewed all changed files for hallucinated APIs/methods
- [ ] Verified no existing contracts broken (backward compatibility)
- [ ] Checked method/class sizes against constitution limits
- [ ] Confirmed all 6 other principles are satisfied
- [ ] Validated edge cases handled (null, empty, concurrent)
- [ ] Logging added and reviewed for debuggability
- [ ] Automatic tests cover all scenarios (including failure cases)
- [ ] Semi-automatic tests created for code improvements
- [ ] Change is small enough to review (<400 lines per commit)
- [ ] AI feedback artifacts created for any mistakes found

**AI Feedback Mechanisms:**
- Store AI feedback in `docs/ai-feedback/` with date and context
- Create failing tests that demonstrate AI hallucinations
- Document correct patterns in `docs/ai-patterns/`
- Use spec-kit tasks to track AI feedback resolution
- Review AI feedback monthly to identify recurring issues

**Logging Review:**
- All new features MUST include logging tasks
- Log output MUST be reviewed for clarity and usefulness
- Log files MUST be rotated and size-limited in production

---

## Governance

This constitution supersedes all other development practices in the project.
Compliance is mandatory unless explicitly waived by project maintainers.

**Amendment Process:**
1. Propose change as a commit to this file
2. Provide rationale and impact analysis
3. Require unanimous maintainer approval
4. Update version according to semantic versioning
5. Document migration plan if behavior changes

**Versioning Policy:**
- MAJOR: Backward-incompatible principle changes or removals
- MINOR: New principles or material guidance additions
- PATCH: Clarifications, wording improvements, typo fixes

**Compliance Review:**
- All commits MUST include constitution compliance checklist
- Quarterly review of constitution effectiveness
- Violations MUST be documented and remediated

**Version**: 2.0.0 | **Ratified**: 2026-02-21 | **Last Amended**: 2026-02-21
