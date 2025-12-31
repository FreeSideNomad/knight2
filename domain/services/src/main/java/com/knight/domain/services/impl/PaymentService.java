package com.knight.domain.services.impl;

import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.services.Service;
import com.knight.domain.services.ServiceCategory;
import com.knight.platform.sharedkernel.Action;
import com.knight.platform.sharedkernel.ActionType;
import com.knight.platform.sharedkernel.ServiceType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Payment service for processing financial transactions.
 *
 * <p>Provides actions for submitting, approving, and managing payments.
 */
@Component
public class PaymentService extends Service {

    private static final String SERVICE_IDENTIFIER = "payment";

    private static final List<Action> ACTIONS = List.of(
        Action.of(ServiceType.DIRECT, SERVICE_IDENTIFIER, "payment", ActionType.CREATE),
        Action.of(ServiceType.DIRECT, SERVICE_IDENTIFIER, "payment", ActionType.VIEW),
        Action.of(ServiceType.DIRECT, SERVICE_IDENTIFIER, "payment", ActionType.APPROVE),
        Action.of(ServiceType.DIRECT, SERVICE_IDENTIFIER, "payment", ActionType.DELETE),
        Action.of(ServiceType.DIRECT, SERVICE_IDENTIFIER, "payment-history", ActionType.VIEW)
    );

    @Override
    public String getServiceIdentifier() {
        return SERVICE_IDENTIFIER;
    }

    @Override
    public ServiceCategory getServiceCategory() {
        return ServiceCategory.PAYMENT;
    }

    @Override
    public String getDisplayName() {
        return "Payment Service";
    }

    @Override
    public String getDescription() {
        return "Process and manage financial payment transactions including wire transfers, ACH, and domestic payments.";
    }

    @Override
    public boolean isAccountEligible(ClientAccount account) {
        // Account must be active
        if (!account.isActive()) {
            return false;
        }

        // All active accounts are eligible for payment service
        return true;
    }

    @Override
    public List<Action> getActions() {
        return ACTIONS;
    }
}
