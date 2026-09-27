package com.mss.base.response;

import com.mss.base.entity.User;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserResponse {

    private Integer id;
    private String firstName;
    private String lastName;
    private String email;
    private String normalizedEmail;
    private Boolean isEmailVerified;
    private String status;
    private String firebaseUserId;

    public static UserResponse from(User user) {
        if (user == null) {
            return null; // or return a default UserResponse object
        }
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setEmail(user.getEmail());
        response.setIsEmailVerified(user.getIsEmailVerified());
        response.setStatus(String.valueOf(user.getUserStatus()));
        response.setFirebaseUserId(user.getFirebaseUserId());
        return response;
    }
}
