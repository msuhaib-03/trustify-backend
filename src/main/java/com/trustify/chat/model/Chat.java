package com.trustify.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Document(collection = "chats")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Chat {
    @Id
    private String id;

//    private String user1Id; // buyer
//    private String user2Id; // seller

    private Set<String> participants = new HashSet<>();

    private Instant updatedAt;

    private List<Message> messages = new ArrayList<>();
}
