package com.rayhan.base.service;

import com.rayhan.base.entity.User;

import java.util.Map;
import java.util.Optional;

/**
 * Service interface for managing user-related operations.
 */
public interface UserService {

    /**
     * Checks if a user exists by their email address and returns an optional user entity.
     *
     * @param email the email address to search for
     * @return an {@link Optional} containing the {@link User} if found, or empty if not found
     */
    Optional<User> findByEmailExist(String email);

    /**
     * Searches for users matching the provided email with pagination and sorting options.
     *
     * @param email  the email address to search for (can be a partial match)
     * @param page   the page number for pagination (zero-based)
     * @param size   the number of records per page
     * @param sortBy the field to sort the results by
     * @return a {@link Map} containing the search results and metadata (e.g., total count, user list)
     */
    Map<String, Object> searchUserList(String email, Integer page, Integer size, String sortBy);
}