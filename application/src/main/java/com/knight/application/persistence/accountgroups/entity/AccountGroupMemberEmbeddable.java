package com.knight.application.persistence.accountgroups.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class AccountGroupMemberEmbeddable {

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    public AccountGroupMemberEmbeddable() {}

    public AccountGroupMemberEmbeddable(String accountId, Instant addedAt) {
        this.accountId = accountId;
        this.addedAt = addedAt;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountGroupMemberEmbeddable that = (AccountGroupMemberEmbeddable) o;
        return Objects.equals(accountId, that.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId);
    }
}
