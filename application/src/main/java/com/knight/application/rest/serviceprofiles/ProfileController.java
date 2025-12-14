package com.knight.application.rest.serviceprofiles;

import com.knight.application.rest.serviceprofiles.dto.OnlineProfileDto;
import com.knight.application.rest.serviceprofiles.dto.ProfileSummaryDto;
import com.knight.application.rest.serviceprofiles.dto.ServicingProfileDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for client profile management.
 *
 * MVP Implementation: Returns empty lists as placeholders.
 * Future implementations will integrate with domain services to retrieve actual profile data.
 */
@RestController
@RequestMapping("/api/clients/{clientId}/profiles")
public class ProfileController {

    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);

    /**
     * Get all profiles for a client (summary view).
     *
     * @param clientId the client identifier
     * @return list of profile summaries (empty for MVP)
     */
    @GetMapping
    public List<ProfileSummaryDto> getClientProfiles(@PathVariable String clientId) {
        logger.info("MVP: Getting profiles for client: {} - returning empty list", clientId);
        // TODO: Implement actual profile retrieval once domain integration is complete
        return List.of();
    }

    /**
     * Get all servicing profiles for a client.
     *
     * @param clientId the client identifier
     * @return list of servicing profiles (empty for MVP)
     */
    @GetMapping("/servicing")
    public List<ServicingProfileDto> getServicingProfiles(@PathVariable String clientId) {
        logger.info("MVP: Getting servicing profiles for client: {} - returning empty list", clientId);
        // TODO: Implement actual servicing profile retrieval once domain integration is complete
        return List.of();
    }

    /**
     * Get all online profiles for a client.
     *
     * @param clientId the client identifier
     * @return list of online profiles (empty for MVP)
     */
    @GetMapping("/online")
    public List<OnlineProfileDto> getOnlineProfiles(@PathVariable String clientId) {
        logger.info("MVP: Getting online profiles for client: {} - returning empty list", clientId);
        // TODO: Implement actual online profile retrieval once domain integration is complete
        return List.of();
    }
}
