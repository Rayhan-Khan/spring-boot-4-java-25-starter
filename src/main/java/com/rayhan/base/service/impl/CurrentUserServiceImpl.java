package com.rayhan.base.service.impl;

import com.rayhan.base.dto.UpdateUserRequest;
import com.rayhan.base.entity.User;
import com.rayhan.base.enums.ReferenceType;
import com.rayhan.base.exception.ResourceNotFoundException;
import com.rayhan.base.repository.UserRepository;
import com.rayhan.base.response.CurrentUserResponse;
import com.rayhan.base.response.MediaStorageResponse;
import com.rayhan.base.service.CurrentUserService;
import com.rayhan.base.service.FirebaseStorageService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Implementation of the CurrentUserService interface. Users are identified by their internal user ID,
 * whichever login method they used.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserServiceImpl implements CurrentUserService {

    private static final Logger logger = LoggerFactory.getLogger(CurrentUserServiceImpl.class);
    private final UserRepository userRepository;
    private final FirebaseStorageService storageService;

    @Value("${app.user.join-date-format}")
    private String userJoinDateFormat;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void setAvatar(MultipartFile file, Integer userId) throws IOException {
        // Validate input
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty.");
        }

        User user = findUserOrThrow(userId);

        // Upload file to Firebase Storage
        storageService.uploadFile(file, user.getId(), ReferenceType.UsersAvatars, user.getId());

        userRepository.save(user);
        logger.info("Avatar updated successfully for user {}", userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public CurrentUserResponse getCurrentUser(Integer userId) {
        logger.debug("Fetching user data for user {}", userId);

        User user = findUserOrThrow(userId);

        // Prepare response
        CurrentUserResponse response = new CurrentUserResponse();
        response.setId(user.getId());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setEmail(user.getEmail());
        response.setIsEmailVerified(user.getIsEmailVerified());
        response.setStatus(user.getUserStatus().name());
        response.setRole(user.getRole().name());
        response.setFirebaseUserId(user.getFirebaseUserId());
        response.setJoinDate(user.getCreatedAt().format(DateTimeFormatter.ofPattern(userJoinDateFormat)));

        Optional.ofNullable(storageService.getMediaStorage(user.getId(), ReferenceType.UsersAvatars))
                .map(MediaStorageResponse::getUrl)
                .ifPresent(response::setAvatar);

        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateCurrentUser(Integer userId, UpdateUserRequest request) {
        User user = findUserOrThrow(userId);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        userRepository.save(user);
        logger.info("Updated user data for user {}", userId);
    }

    private User findUserOrThrow(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }
}
