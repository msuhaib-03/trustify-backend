package com.trustify.controller;

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

     // This controller will handle endpoints related to CNIC verification, such as submitting CNIC details and fetching verification status.

    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<?> submitVerification(
            @RequestParam("frontImage") MultipartFile frontImage,
            @RequestParam("backImage") MultipartFile backImage,
            Principal principal
    ) {

        return ResponseEntity.ok(
                cnicVerificationService.submitVerifiction(
                        principal.getName(),
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
