package com.knight.portal.services.dto;

import java.time.Instant;

/**
 * Response DTO for service enrollment.
 */
public class EnrollServiceResponse {
    private String enrollmentId;
    private String serviceType;
    private String status;
    private Instant enrolledAt;
    private int linkedAccountCount;

    public String getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(String enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(Instant enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public int getLinkedAccountCount() {
        return linkedAccountCount;
    }

    public void setLinkedAccountCount(int linkedAccountCount) {
        this.linkedAccountCount = linkedAccountCount;
    }
}
