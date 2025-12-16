package com.knight.domain.serviceprofiles.repository;

import com.knight.domain.serviceprofiles.aggregate.Profile;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.ProfileId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository interface for Profile aggregate persistence.
 * Implementations handle the actual storage mechanism (JPA, in-memory, etc.)
 */
public interface ServicingProfileRepository {

    /**
     * Persists a Profile aggregate.
     *
     * @param profile the profile to save
     */
    void save(Profile profile);

    /**
     * Retrieves a Profile by its identifier.
     *
     * @param profileId the profile identifier
     * @return the profile if found
     */
    Optional<Profile> findById(ProfileId profileId);

    /**
     * Find all profiles where the given client is the primary client.
     */
    List<Profile> findByPrimaryClient(ClientId clientId);

    /**
     * Find all profiles where the given client is a secondary client (not primary).
     */
    List<Profile> findBySecondaryClient(ClientId clientId);

    /**
     * Find all profiles where the given client is enrolled (either primary or secondary).
     */
    List<Profile> findByClient(ClientId clientId);

    /**
     * Check if client is already primary in a SERVICING profile.
     * Used to enforce: "A client can be primary in only ONE servicing profile"
     */
    boolean existsServicingProfileWithPrimaryClient(ClientId clientId);

    // ==================== Paginated Search Methods ====================

    /**
     * Page result for repository queries.
     */
    record PageResult<T>(List<T> content, long totalElements, int page, int size) {
        public int totalPages() {
            return size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        }
    }

    /**
     * Search profiles where the given client is primary, filtered by profile types.
     */
    PageResult<Profile> searchByPrimaryClient(ClientId clientId, Set<String> profileTypes, int page, int size);

    /**
     * Search profiles where the given client is enrolled (primary or secondary), filtered by profile types.
     */
    PageResult<Profile> searchByClient(ClientId clientId, Set<String> profileTypes, int page, int size);

    /**
     * Search profiles by client name (primary client only), filtered by profile types.
     */
    PageResult<Profile> searchByPrimaryClientName(String clientName, Set<String> profileTypes, int page, int size);

    /**
     * Search profiles by client name (any enrolled client), filtered by profile types.
     */
    PageResult<Profile> searchByClientName(String clientName, Set<String> profileTypes, int page, int size);
}
