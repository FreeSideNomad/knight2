# Claude Code Guidelines for Knight Platform

## Critical Rules

### Test Coverage Requirements (MANDATORY - NO EXCEPTIONS)

**Before any commit and push:**

1. **Line Coverage**: Must achieve ≥80% line coverage
2. **Branch Coverage**: Must achieve ≥80% branch coverage
3. **No Exceptions**: If coverage is not achieved, focus ONLY on improving coverage until targets are met
4. **Report Blockers**: If stuck on achieving coverage, STOP and report the issue immediately
5. **Commit Gate**: Do NOT commit or push until both coverage thresholds are met

```bash
# Run tests with coverage
./mvnw clean verify -Pcoverage

# Check coverage reports at:
# target/site/jacoco/index.html
```

### Implementation Workflow

1. Implement user story code
2. Write comprehensive unit tests
3. Write integration tests where needed
4. Run coverage check: `./mvnw clean verify -Pcoverage`
5. **IF coverage < 80%**: Add more tests, do NOT proceed
6. **IF coverage ≥ 80%**: Commit and push with story reference
7. Move to next story

### Commit Message Format

```
US-XX-NNN: Brief description

- Bullet points of changes
- Test coverage: XX% line, XX% branch

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

## Project Structure

- `kernel/` - Shared kernel (value objects, common types)
- `domain/` - Domain modules (clients, profiles, etc.)
- `application/` - Main application with REST API and persistence
- `employee-portal/` - Vaadin employee portal
- `client-portal/` - Vaadin client portal
- `indirect-client-portal/` - Vaadin indirect client portal

## Testing Standards

### Unit Tests
- Test all public methods
- Test edge cases and error conditions
- Mock external dependencies
- Use descriptive test names: `should_doSomething_when_condition`

### Integration Tests
- Test API endpoints with `@SpringBootTest`
- Use `@Testcontainers` for database tests
- Test happy path and error scenarios

### Coverage Tools
- JaCoCo for coverage measurement
- Minimum thresholds enforced in build

## Code Style

- Follow existing patterns in codebase
- Use records for value objects
- Use Optional for nullable returns
- Prefer composition over inheritance
- Keep methods focused and small

## Parallel Task Execution

When implementing multiple independent stories:
- Launch parallel Task agents for non-dependent work
- Wait for dependencies before starting dependent stories
- Coordinate commits to avoid conflicts
