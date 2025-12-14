package com.knight.domain.users.repository;

import com.knight.domain.users.aggregate.User;
import com.knight.platform.sharedkernel.UserId;

import java.util.Optional;

/**
 * Repository interface for User aggregate persistence.
 * Implementations handle the actual storage mechanism (JPA, in-memory, etc.)
 */
public interface UserRepository {

    /**
     * Persists a User aggregate.
     *
     * @param user the user to save
     */
    void save(User user);

    /**
     * Retrieves a User by its identifier.
     *
     * @param userId the user identifier
     * @return the user if found
     */
    Optional<User> findById(UserId userId);
}
