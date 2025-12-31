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
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PaymentServiceTest {

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService();
    }

    @Test
    void shouldReturnCorrectServiceIdentifier() {
        assertThat(paymentService.getServiceIdentifier()).isEqualTo("payment");
    }

    @Test
    void shouldReturnPaymentCategory() {
        assertThat(paymentService.getServiceCategory()).isEqualTo(ServiceCategory.PAYMENT);
    }

    @Test
    void shouldReturnDisplayName() {
        assertThat(paymentService.getDisplayName()).isEqualTo("Payment Service");
    }

    @Test
    void shouldReturnDescription() {
        assertThat(paymentService.getDescription())
            .contains("payment")
            .contains("transaction");
    }

    @Test
    void shouldBeEligibleForActiveAccount() {
        ClientAccount account = createActiveAccount();
        assertThat(paymentService.isAccountEligible(account)).isTrue();
    }

    @Test
    void shouldNotBeEligibleForClosedAccount() {
        ClientAccount account = createClosedAccount();
        assertThat(paymentService.isAccountEligible(account)).isFalse();
    }

    @Test
    void shouldReturnExpectedActions() {
        List<Action> actions = paymentService.getActions();

        assertThat(actions).isNotEmpty();

        // Verify expected action types are present
        List<ActionType> actionTypes = actions.stream()
            .map(Action::actionType)
            .toList();

        assertThat(actionTypes).contains(ActionType.CREATE, ActionType.VIEW);
    }

    @Test
    void shouldHaveActionsWithCorrectServiceType() {
        List<Action> actions = paymentService.getActions();

        assertThat(actions).allSatisfy(action ->
            assertThat(action.serviceType()).isEqualTo(ServiceType.DIRECT)
        );
    }

    @Test
    void shouldHaveActionsWithCorrectServiceIdentifier() {
        List<Action> actions = paymentService.getActions();

        assertThat(actions).allSatisfy(action ->
            assertThat(action.service()).isEqualTo("payment")
        );
    }

    @Test
    void shouldReturnImmutableActionsList() {
        List<Action> actions = paymentService.getActions();

        assertThatThrownBy(() -> actions.add(
            Action.of(ServiceType.DIRECT, "payment", "test", ActionType.VIEW)
        )).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldProvideCompleteServiceInfo() {
        ServiceInfo info = paymentService.getServiceInfo();

        assertThat(info.identifier()).isEqualTo("payment");
        assertThat(info.category()).isEqualTo(ServiceCategory.PAYMENT);
        assertThat(info.displayName()).isEqualTo("Payment Service");
        assertThat(info.description()).isNotBlank();
        assertThat(info.actions()).isNotEmpty();
    }

    @Test
    void shouldHavePaymentCreateAction() {
        List<Action> actions = paymentService.getActions();

        boolean hasPaymentCreate = actions.stream()
            .anyMatch(a -> a.resourceType().equals("payment") && a.actionType() == ActionType.CREATE);

        assertThat(hasPaymentCreate).isTrue();
    }

    @Test
    void shouldHavePaymentViewAction() {
        List<Action> actions = paymentService.getActions();

        boolean hasPaymentView = actions.stream()
            .anyMatch(a -> a.resourceType().equals("payment") && a.actionType() == ActionType.VIEW);

        assertThat(hasPaymentView).isTrue();
    }

    @Test
    void shouldHavePaymentApproveAction() {
        List<Action> actions = paymentService.getActions();

        boolean hasPaymentApprove = actions.stream()
            .anyMatch(a -> a.resourceType().equals("payment") && a.actionType() == ActionType.APPROVE);

        assertThat(hasPaymentApprove).isTrue();
    }

    @Test
    void shouldHavePaymentHistoryViewAction() {
        List<Action> actions = paymentService.getActions();

        boolean hasPaymentHistoryView = actions.stream()
            .anyMatch(a -> a.resourceType().equals("payment-history") && a.actionType() == ActionType.VIEW);

        assertThat(hasPaymentHistoryView).isTrue();
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
