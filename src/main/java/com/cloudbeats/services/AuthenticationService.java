package com.cloudbeats.services;

import com.cloudbeats.db.entities.ApplicationUser;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    public static class User {
        private int userId;
        private String userName;
        private String email;

        public User(int userId, String userName, String email) {
            this.userId = userId;
            this.userName = userName;
            this.email = email;
        }

        public int getUserId() {
            return userId;
        }

        public String getUserName() {
            return userName;
        }

        public String getEmail() {
            return email;
        }
    }

    public User validateUserCredentials(String userName, String password, String email) {
        // Replace with actual database lookup logic
        return new User(1, "Test User", email);
    }
}
