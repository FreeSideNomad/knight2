package com.knight.domain.services.impl;

import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.services.ServiceCategory;
import com.knight.domain.services.ServiceInfo;
import com.knight.platform.sharedkernel.AccountStatus;
import com.knight.platform.sharedkernel.AccountSystem;
import com.knight.platform.sharedkernel.Action;
import com.knight.platform.sharedkernel.ActionType;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.Currency;
import com.knight.platform.sharedkernel.ServiceType;
import com.knight.platform.sharedkernel.SrfClientId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ReportingServiceTest {

    private ReportingService reportingService;

    @BeforeEach
    void setUp() {
        reportingService = new ReportingService();
    }

    @Test
    void shouldReturnCorrectServiceIdentifier() {
        assertThat(reportingService.getServiceIdentifier()).isEqualTo("reporting");
    }

    @Test
    void shouldReturnReportingCategory() {
        assertThat(reportingService.getServiceCategory()).isEqualTo(ServiceCategory.REPORTING);
    }

    @Test
    void shouldReturnDisplayName() {
        assertThat(reportingService.getDisplayName()).isEqualTo("Reporting Service");
    }

    @Test
    void shouldReturnDescription() {
        assertThat(reportingService.getDescription())
            .contains("report");
    }

    @Test
    void shouldBeEligibleForActiveAccount() {
        ClientAccount account = createActiveAccount();
        assertThat(reportingService.isAccountEligible(account)).isTrue();
    }

    @Test
    void shouldBeEligibleForClosedAccount() {
        // Reporting allows historical access to closed accounts
        ClientAccount account = createClosedAccount();
        assertThat(reportingService.isAccountEligible(account)).isTrue();
    }

    @Test
    void shouldReturnExpectedActions() {
        List<Action> actions = reportingService.getActions();

        assertThat(actions).isNotEmpty();

        List<ActionType> actionTypes = actions.stream()
            .map(Action::actionType)
            .toList();

        assertThat(actionTypes).contains(ActionType.VIEW, ActionType.CREATE);
    }

    @Test
    void shouldHaveActionsWithCorrectServiceType() {
        List<Action> actions = reportingService.getActions();

        assertThat(actions).allSatisfy(action ->
            assertThat(action.serviceType()).isEqualTo(ServiceType.DIRECT)
        );
    }

    @Test
    void shouldHaveActionsWithCorrectServiceIdentifier() {
        List<Action> actions = reportingService.getActions();

        assertThat(actions).allSatisfy(action ->
            assertThat(action.service()).isEqualTo("reporting")
        );
    }

    @Test
    void shouldReturnImmutableActionsList() {
        List<Action> actions = reportingService.getActions();

        assertThatThrownBy(() -> actions.add(
            Action.of(ServiceType.DIRECT, "reporting", "test", ActionType.VIEW)
        )).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldProvideCompleteServiceInfo() {
        ServiceInfo info = reportingService.getServiceInfo();

        assertThat(info.identifier()).isEqualTo("reporting");
        assertThat(info.category()).isEqualTo(ServiceCategory.REPORTING);
        assertThat(info.displayName()).isEqualTo("Reporting Service");
        assertThat(info.description()).isNotBlank();
        assertThat(info.actions()).isNotEmpty();
    }

    @Test
    void shouldHaveReportViewAction() {
        List<Action> actions = reportingService.getActions();

        boolean hasReportView = actions.stream()
            .anyMatch(a -> a.resourceType().equals("report") && a.actionType() == ActionType.VIEW);

        assertThat(hasReportView).isTrue();
    }

    @Test
    void shouldHaveReportCreateAction() {
        List<Action> actions = reportingService.getActions();

        boolean hasReportCreate = actions.stream()
            .anyMatch(a -> a.resourceType().equals("report") && a.actionType() == ActionType.CREATE);

        assertThat(hasReportCreate).isTrue();
    }

    @Test
    void shouldHaveScheduledReportCreateAction() {
        List<Action> actions = reportingService.getActions();

        boolean hasScheduledReportCreate = actions.stream()
            .anyMatch(a -> a.resourceType().equals("scheduled-report") && a.actionType() == ActionType.CREATE);

        assertThat(hasScheduledReportCreate).isTrue();
    }

    @Test
    void shouldHaveScheduledReportManageAction() {
        List<Action> actions = reportingService.getActions();

        boolean hasScheduledReportManage = actions.stream()
            .anyMatch(a -> a.resourceType().equals("scheduled-report") && a.actionType() == ActionType.MANAGE);

        assertThat(hasScheduledReportManage).isTrue();
    }

    @Test
    void shouldDifferentiateFromPaymentServiceEligibility() {
        // Reporting is more lenient - allows closed accounts
        ClientAccount closedAccount = createClosedAccount();

        ReportingService reporting = new ReportingService();
        PaymentService payment = new PaymentService();

        assertThat(reporting.isAccountEligible(closedAccount)).isTrue();
        assertThat(payment.isAccountEligible(closedAccount)).isFalse();
    }

    private ClientAccount createActiveAccount() {
        return ClientAccount.create(
            new ClientAccountId(AccountSystem.CAN_DDA, "DDA", "12345:123456789012"),
            new SrfClientId("123456789"),
            Currency.USD
        );
    }

    private ClientAccount createClosedAccount() {
        return ClientAccount.reconstruct(
            new ClientAccountId(AccountSystem.CAN_DDA, "DDA", "12345:123456789012"),
            new SrfClientId("123456789"),
            null,
            Currency.USD,
            null,
            AccountStatus.CLOSED,
            Instant.now(),
            Instant.now()
        );
    }
}
