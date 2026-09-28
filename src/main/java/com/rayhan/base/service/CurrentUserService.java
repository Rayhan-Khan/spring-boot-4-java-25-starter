package com.rayhan.base.service;

import com.rayhan.base.dto.UpdateUserRequest;
import com.rayhan.base.response.CurrentUserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface CurrentUserService {

    /**
     * Sets the avatar for the specified user.
     *
     * @param file           the file containing the avatar image.
     * @param userId the internal ID of the current user.
     * @throws IOException if an error occurs while processing the avatar file.
     */
    void setAvatar(MultipartFile file, Integer userId) throws IOException;

    /**
     * Fetch details of the currently authenticated user.
     *
     * @param userId the internal ID of the current user, taken from the access token.
     * @return CurrentUserResponse containing the user details.
     */
    CurrentUserResponse getCurrentUser(Integer userId);

    /**
     * Update the currently authenticated user's details.
     *
     * @param userId the internal ID of the current user, taken from the access token.
     * @param request        the request payload containing the updated user details.
     */
    void updateCurrentUser(Integer userId, UpdateUserRequest request);
}
