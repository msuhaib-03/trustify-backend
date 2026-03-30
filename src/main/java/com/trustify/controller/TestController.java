package com.trustify.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {
    @GetMapping("/public")
    public String publicAccess() {
        return "✅ Public endpoint — anyone can access";
    }

    @GetMapping("/user")
    @PreAuthorize("hasRole('USER')")
    public String userAccess() {
        return "👤 USER access granted";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminAccess() {
        return "🛡️ ADMIN access granted";
    }

    @GetMapping("/both")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public String bothAccess() {
        return "✅ USER or ADMIN access granted";
    }
}
