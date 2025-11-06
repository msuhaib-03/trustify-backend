package com.trustify.chat.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class ChatTokenUtil {
    private final String SECRET = "TaK+HaV^uvCHEFsEVfypW#7g9^k*Z8$V"; // put inside env later

    public String generateChatToken(String userId) {
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000 * 24)) // 24 hours
                .signWith(SignatureAlgorithm.HS256, SECRET)
                .compact();
    }

}
