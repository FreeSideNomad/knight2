# Application Module Code Coverage Plan

## Current State (January 2026) - Updated After Phase 1

### Overall Metrics

| Metric | Previous | Current | Target | Status |
|--------|----------|---------|--------|--------|
| **Line Coverage** | 75.73% | **82.48%** | 80% | ✅ ACHIEVED |
| **Branch Coverage** | 62.91% | **67.84%** | 80% | ❌ Gap: 12.16% |
| **Total Tests** | 701 | **780** | - | +79 tests added |

### Progress Summary

Phase 1 has been completed successfully:
- ✅ Line coverage exceeded 80% target (82.48%)
- ❌ Branch coverage still needs improvement (67.84% vs 80% target)
- Added 79 new tests across controllers

### Previous State (for reference)

| Metric | Value |
|--------|-------|
| **Line Coverage** | 75.73% |
| **Branch Coverage** | 62.91% |
| **Total Tests** | 701 |

### Classes with Highest Impact (Lines Missed)

| Class | Lines Missed | Current Coverage | Priority |
|-------|-------------|------------------|----------|
| BankAdminController | 926 | 70.6% | HIGH |
| DirectClientController | 856 | 46.1% | HIGH |
| Auth0Adapter | 711 | 74.2% | MEDIUM |
| IndirectClientBffController | 704 | 64.2% | HIGH |
| AccountGroupRepositoryAdapter | 73 | 11.0% | MEDIUM |
| UserGroupRepositoryAdapter | 73 | 24.7% | MEDIUM |
| MultiIssuerJwtDecoder | 60 | 14.3% | LOW |
| UserRoleId (entity) | 56 | 5.1% | LOW |
| GlobalExceptionHandler | 42 | 53.8% | MEDIUM |
| BatchRepositoryAdapter | 36 | 60.4% | LOW |

### Classes with Worst Branch Coverage

| Class | Branches Missed | Current Coverage | Priority |
|-------|----------------|------------------|----------|
| IndirectClientBffController | 71 | 39.8% | HIGH |
| BankAdminController | 69 | 58.4% | HIGH |
| Auth0Adapter | 59 | 56.6% | MEDIUM |
| DirectClientController | 46 | 42.5% | HIGH |
| SecurityConfiguration | 9 | 50.0% | LOW |

---

## Phase 1: Controller Coverage (Target: +400 lines, +50 branches)

### 1.1 DirectClientController (856 lines missed, 46.1% covered)

**Current Tests:** `DirectClientControllerTest.java` (unit), `DirectClientControllerE2ETest.java` (E2E)

**Missing Coverage Areas:**
- Error handling paths (404, 400, 403 responses)
- Pagination edge cases
- Empty result handling
- User permission validation branches

**Actions:**
1. Add E2E tests for error scenarios:
   - Invalid client IDs
   - Non-existent indirect clients
   - Unauthorized access attempts
2. Add tests for pagination boundaries
3. Add tests for user operations (add, deactivate, list)
4. Cover all `@ExceptionHandler` paths

**Estimated Impact:** +200 lines, +20 branches

### 1.2 IndirectClientBffController (704 lines missed, 64.2% covered)

**Current Tests:** `IndirectClientBffControllerTest.java` (unit)

**Missing Coverage Areas:**
- OFI account management (create, update, deactivate)
- Account group operations
- User group operations
- Related person CRUD operations
- Permission policy operations

**Actions:**
1. Create `IndirectClientBffControllerE2ETest.java` with full integration tests
2. Add tests for:
   - OFI account lifecycle (create → update → deactivate)
   - Account group CRUD with validation
   - User group CRUD with member management
   - Related person add/update/remove
   - Policy listing and assignment

**Estimated Impact:** +250 lines, +35 branches

### 1.3 BankAdminController (926 lines missed, 70.6% covered)

**Current Tests:** `BankAdminControllerE2ETest.java` (28 tests)

**Missing Coverage Areas:**
- Batch processing endpoints (upload, validate, process)
- User management (create, list, deactivate)
- Account group management
- User group management
- Policy management endpoints
- Indirect client/profile management

**Actions:**
1. Expand `BankAdminControllerE2ETest.java` with:
   - Batch upload and processing tests
   - User CRUD operations
   - Account/User group management
   - Policy assignment tests
2. Add error scenario tests for all endpoints

**Estimated Impact:** +300 lines, +30 branches

---

## Phase 2: Service & Adapter Coverage (Target: +200 lines, +30 branches)

### 2.1 Auth0Adapter (711 lines missed, 74.2% covered)

**Current Tests:** `Auth0AdapterTest.java` (49 tests)

**Missing Coverage Areas:**
- CIBA (Client Initiated Backchannel Authentication) edge cases
- MFA enrollment and verification paths
- Step-up authentication flows
- Error handling for network failures
- Token parsing edge cases

**Actions:**
1. Add tests for CIBA timeout and error scenarios
2. Add tests for MFA SMS/TOTP enrollment failures
3. Add step-up verification error cases
4. Mock HTTP client errors

**Estimated Impact:** +150 lines, +25 branches

### 2.2 Repository Adapters

**AccountGroupRepositoryAdapter (73 lines, 11.0%):**
- Add unit tests for all CRUD operations
- Test entity mapping

**UserGroupRepositoryAdapter (73 lines, 24.7%):**
- Add unit tests for member management
- Test group queries

**BatchRepositoryAdapter (36 lines, 60.4%):**
- Add tests for batch status updates
- Test item processing

**Actions:**
1. Create `AccountGroupRepositoryAdapterTest.java`
2. Create `UserGroupRepositoryAdapterTest.java`
3. Expand `BatchRepositoryAdapterTest.java`

**Estimated Impact:** +100 lines, +10 branches

---

## Phase 3: Security & Config Coverage (Target: +100 lines, +15 branches)

### 3.1 MultiIssuerJwtDecoder (60 lines, 14.3%)

**Actions:**
1. Create `MultiIssuerJwtDecoderTest.java`
2. Test each issuer type (Auth0, Entra ID, Portal)
3. Test invalid token handling
4. Test issuer selection logic

### 3.2 SecurityConfiguration (53 lines, 82.0%)

**Actions:**
1. Add tests for security filter chain configuration
2. Test CORS configuration
3. Test endpoint security rules

### 3.3 GlobalExceptionHandler (42 lines, 53.8%)

**Actions:**
1. Add tests for each exception type handling
2. Test error response formatting
3. Test logging behavior

**Estimated Impact:** +100 lines, +15 branches

---

## Phase 4: Entity & DTO Coverage (Target: +150 lines)

### Low-Hanging Fruit (0% coverage DTOs)

| Class | Lines | Action |
|-------|-------|--------|
| UserCheckResponse | 66 | Add record accessor tests |
| ProfileEntity | 39 | Add entity mapping tests |
| IndirectProfileSummaryDto | 33 | Add DTO construction tests |
| UserGroupDetailDto | 27 | Add DTO construction tests |
| AccountGroupDetailDto | 27 | Add DTO construction tests |

**Actions:**
1. Create `DtoConstructionTest.java` with parameterized tests for all DTOs
2. Add entity tests via mapper tests (already partially covered)

**Estimated Impact:** +150 lines

---

## Implementation Priority

### Week 1: High Priority Controllers
- [ ] DirectClientController E2E error scenarios
- [ ] IndirectClientBffController E2E tests
- [ ] BankAdminController batch/user tests

### Week 2: Services & Adapters
- [ ] Auth0Adapter edge cases
- [ ] Repository adapter unit tests
- [ ] PayorEnrolmentProcessor tests

### Week 3: Security & Cleanup
- [ ] MultiIssuerJwtDecoder tests
- [ ] GlobalExceptionHandler tests
- [ ] DTO/Entity coverage

---

## Test File Inventory

### Existing Test Files

| File | Type | Tests | Coverage Impact |
|------|------|-------|-----------------|
| `DirectClientControllerTest.java` | Unit | 24 | Low (mocks) |
| `DirectClientControllerE2ETest.java` | E2E | 38 | Medium |
| `IndirectClientBffControllerTest.java` | Unit | 38 | Low (mocks) |
| `BankAdminControllerE2ETest.java` | E2E | 28 | Medium |
| `Auth0AdapterTest.java` | Unit | 49 | High |

### Tests To Create

| File | Type | Estimated Tests | Priority |
|------|------|-----------------|----------|
| `IndirectClientBffControllerE2ETest.java` | E2E | 40+ | HIGH |
| `AccountGroupRepositoryAdapterTest.java` | Unit | 10 | MEDIUM |
| `UserGroupRepositoryAdapterTest.java` | Unit | 10 | MEDIUM |
| `MultiIssuerJwtDecoderTest.java` | Unit | 8 | MEDIUM |
| `GlobalExceptionHandlerTest.java` | Unit | 10 | LOW |
| `DtoConstructionTest.java` | Unit | 20 | LOW |

---

## Coverage Projection

| Phase | Lines Added | Line Coverage | Branch Coverage |
|-------|-------------|---------------|-----------------|
| Current | - | 75.73% | 62.91% |
| Phase 1 | +400 | 78.0% | 68.0% |
| Phase 2 | +200 | 79.1% | 72.0% |
| Phase 3 | +100 | 79.7% | 75.0% |
| Phase 4 | +150 | **80.5%** | 76.0% |

**Note:** Branch coverage of 80% will require focused attention on conditional logic in controllers and Auth0Adapter. Each branch requires specific test scenarios for both true/false paths.

---

## Recommendations

1. **Focus on E2E tests over unit tests for controllers** - Unit tests with mocks don't contribute to coverage because the actual code isn't executed.

2. **Prioritize branch coverage** - Current 62.91% is far from 80%. Focus on error handling paths and conditional logic.

3. **Consider coverage exclusions carefully** - The pom.xml excludes many packages. Either:
   - Remove exclusions and add real tests
   - Or accept lower coverage for infrastructure code

4. **Use parameterized tests for DTOs** - One test class with `@ParameterizedTest` can cover many DTOs efficiently.

5. **Mock external services, not internal code** - Auth0Adapter tests should mock the HTTP client, not the adapter itself.
