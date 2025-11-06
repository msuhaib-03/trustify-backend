package com.trustify.chat.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "chats")
@Data
public class Chat {
    @Id
    private String id;

    private String user1Id; // buyer
    private String user2Id; // seller

    private List<Message> messages = new ArrayList<>();
}
