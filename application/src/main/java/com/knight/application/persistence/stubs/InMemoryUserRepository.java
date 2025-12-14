package com.knight.application.persistence.stubs;

import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.repository.UserRepository;
import com.knight.platform.sharedkernel.UserId;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of UserRepository for development/testing.
 */
@Repository
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> store = new ConcurrentHashMap<>();

    @Override
    public void save(User user) {
        store.put(user.id().id(), user);
    }

    @Override
    public Optional<User> findById(UserId userId) {
        return Optional.ofNullable(store.get(userId.id()));
    }
}
