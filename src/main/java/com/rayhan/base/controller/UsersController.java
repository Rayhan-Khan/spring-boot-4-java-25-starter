package com.rayhan.base.controller;

import com.rayhan.base.entity.User;
import com.rayhan.base.response.UserResponse;
import com.rayhan.base.service.UserService;
import com.rayhan.base.utils.ApiResponse;
import com.rayhan.base.utils.CommonDataHelper;
import com.rayhan.base.utils.PaginatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.rayhan.base.utils.ResponseBuilder.paginatedSuccess;
import static org.springframework.http.ResponseEntity.ok;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/users")
@Tag(name = "Users")
public class UsersController {
    private static final Logger logger = LoggerFactory.getLogger(UsersController.class);

    private final UserService service;
    private final CommonDataHelper commonDataHelper;

    @GetMapping
    @Operation(summary = "Retrieve a list of users", description = "Retrieve a list of users with optional filters for email, page, and size.")
    public ResponseEntity<ApiResponse<Object>> getUsers(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "sortBy", defaultValue = "") String sortBy,
            @RequestParam(value = "email", defaultValue = "") String email
    ) {
        Map<String, Object> userMenuMap = service.searchUserList(email, page, size, sortBy);
        PaginatedResponse response = new PaginatedResponse();
        List<User> responses = (List<User>) userMenuMap.get("lists");
        List<UserResponse> customResponses = responses.stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
        commonDataHelper.getCommonData(page, size, userMenuMap, response, customResponses);
        return ok(paginatedSuccess(response));
    }
}