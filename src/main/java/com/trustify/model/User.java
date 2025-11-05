package com.trustify.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Document(collection = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    @NonNull
    private String username;

    @Indexed(unique = true)
    private String email;

    private String phone;
    private String password;

    @Builder.Default
    private Role role = Role.USER;

    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedBy
    private Instant updatedAt;

    private Set<String> favoriteListingIds = new HashSet<>();

    public enum Role {
        USER,
        ADMIN
    }

    public enum AccountStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED
    }
}
