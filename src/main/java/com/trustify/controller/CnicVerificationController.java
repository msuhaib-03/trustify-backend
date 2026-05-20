package com.trustify.controller;

import com.trustify.model.User;
import com.trustify.repository.UserRepository;
import com.trustify.service.CnicVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RequestMapping("/cnic-verification")
@RestController
public class CnicVerificationController {
    @Autowired
    CnicVerificationService cnicVerificationService;
    @Autowired
    private UserRepository userRepository;

    // This controller will handle endpoints related to CNIC verification, such as submitting CNIC details and fetching verification status.

    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<?> submitVerification(
            @RequestParam("frontImage") MultipartFile frontImage,
            @RequestParam("backImage") MultipartFile backImage,
            Principal principal
    ) {

        // logic changed here because we want actual userId and not email.
        // We have been fetching user email instead of actual mongoDB userId in
        // all the services and controllers. So we will change that to userId now.
        // And for that we will need to fetch user details using principal.getName() which is email and then get userId from there and pass it to service layer.
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(
                cnicVerificationService.submitVerifiction(
                      //  principal.getName(), // this was fetching email instead of userId
                        user.getId(), // this fetches actual userId from database using email from principal
                        frontImage,
                        backImage
                )
        );
    }

    @GetMapping("/my-status")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<?> getMyStatus(Principal principal){
        return ResponseEntity.ok(cnicVerificationService.getMyVerificationStatus(principal));
    }

}
