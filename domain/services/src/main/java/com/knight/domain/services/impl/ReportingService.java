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
 * Reporting service for accessing and generating reports.
 *
 * <p>Provides actions for viewing, exporting, and scheduling reports.
 */
@Component
public class ReportingService extends Service {

    private static final String SERVICE_IDENTIFIER = "reporting";

    private static final List<Action> ACTIONS = List.of(
        Action.of(ServiceType.DIRECT, SERVICE_IDENTIFIER, "report", ActionType.VIEW),
        Action.of(ServiceType.DIRECT, SERVICE_IDENTIFIER, "report", ActionType.CREATE),
        Action.of(ServiceType.DIRECT, SERVICE_IDENTIFIER, "report", ActionType.UPDATE),
        Action.of(ServiceType.DIRECT, SERVICE_IDENTIFIER, "report", ActionType.DELETE),
        Action.of(ServiceType.DIRECT, SERVICE_IDENTIFIER, "scheduled-report", ActionType.CREATE),
        Action.of(ServiceType.DIRECT, SERVICE_IDENTIFIER, "scheduled-report", ActionType.MANAGE)
    );

    @Override
    public String getServiceIdentifier() {
        return SERVICE_IDENTIFIER;
    }

    @Override
    public ServiceCategory getServiceCategory() {
        return ServiceCategory.REPORTING;
    }

    @Override
    public String getDisplayName() {
        return "Reporting Service";
    }

    @Override
    public String getDescription() {
        return "Access and generate reports including account statements, transaction history, and analytics.";
    }

    @Override
    public boolean isAccountEligible(ClientAccount account) {
        // Reporting is available for active and closed accounts (historical access)
        // Only truly suspended accounts are ineligible
        return account.isActive() || account.isClosed();
    }

    @Override
    public List<Action> getActions() {
        return ACTIONS;
    }
}
