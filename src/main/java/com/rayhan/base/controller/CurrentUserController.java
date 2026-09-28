package com.rayhan.base.controller;

import com.rayhan.base.annotation.ValidAvatar;
import com.rayhan.base.dto.UpdateUserRequest;
import com.rayhan.base.exception.CustomMessagePresentException;
import com.rayhan.base.response.CurrentUserResponse;
import com.rayhan.base.security.AuthenticatedUser;
import com.rayhan.base.service.CurrentUserService;
import com.rayhan.base.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static com.rayhan.base.utils.ResponseBuilder.error;
import static com.rayhan.base.utils.ResponseBuilder.success;
import static org.springframework.http.ResponseEntity.ok;

/**
 * Endpoints for the logged-in user. Works with every login method: {@link AuthenticatedUser} is the same
 * for the app's own tokens and for Firebase ID tokens.
 */
@RestController
@RequestMapping("/api/v1/current-user")
@RequiredArgsConstructor
@Tag(name = "Current user", description = "Current user info")
public class CurrentUserController {

    private static final Logger logger = LoggerFactory.getLogger(CurrentUserController.class);
    private final CurrentUserService currentUserService;

    /**
     * Endpoint to upload and set the avatar for the authenticated user.
     *
     * @param file        The avatar file to upload. Allowed types and size come from avatar.* settings.
     * @param currentUser The authenticated user.
     * @return ResponseEntity  A response indicating the result of the operation.
     * @throws IllegalArgumentException If validation errors occur during the request.
     */
    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Set user avatar",
            description = "Allows authenticated users to set their avatar. " +
                    "Allowed file types and maximum size are configured with AVATAR_ALLOWED_FORMATS and AVATAR_MAX_SIZE.")
    public ResponseEntity<ApiResponse<Void>> setAvatar(
            @ValidAvatar
            @RequestParam("file") MultipartFile file,

            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            currentUserService.setAvatar(file, currentUser.userId());
            return ok(success(null, "Avatar uploaded successfully."));
        } catch (IllegalArgumentException e) {
            logger.warn("Validation error while setting avatar for user {}: {}", currentUser.userId(), e.getMessage());
            return ResponseEntity.badRequest().body(error(null, e.getMessage()));
        } catch (IOException e) {
            logger.error("IO error while processing avatar for user {}: {}", currentUser.userId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(error(null, e.getMessage()));
        }
    }

    /**
     * Fetch details of the currently authenticated user.
     *
     * @param currentUser the authenticated user.
     * @return ResponseEntity containing the current user details in a structured JSON format.
     */
    @GetMapping
    @Operation(summary = "Get Current User Data", description = "Retrieve details of the currently authenticated user.")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> getCurrentUser(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ok(success(currentUserService.getCurrentUser(currentUser.userId()), null));
    }

    /**
     * Update the currently authenticated user's details.
     *
     * @param currentUser the authenticated user.
     * @param request     the request payload containing the updated user details.
     * @return ResponseEntity indicating the status of the operation.
     */
    @PutMapping
    @Operation(summary = "Update Current User Data", description = "Update the first name and last name of the authenticated user.")
    public ResponseEntity<ApiResponse<Void>> updateCurrentUser(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody UpdateUserRequest request) {
        try {
            currentUserService.updateCurrentUser(currentUser.userId(), request);
            return ok(success(null, "User details updated successfully"));
        } catch (CustomMessagePresentException e) {
            logger.error("Validation error for user {}: {}", currentUser.userId(), e.getMessage());
            return ResponseEntity.badRequest().body(error(null, e.getMessage()));
        }
    }
}
