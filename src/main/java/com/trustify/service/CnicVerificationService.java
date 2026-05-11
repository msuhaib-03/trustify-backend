package com.trustify.service;

import com.trustify.dto.CnicVerificationResponse;
import com.trustify.model.CnicVerification;
import com.trustify.model.User;
import com.trustify.repository.CnicVerificationRepository;
import com.trustify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.swing.text.html.Option;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CnicVerificationService {
    @Autowired
    private S3UploadService s3UploadService;

    @Autowired
    CnicVerificationRepository cnicVerificationRepository;

    @Autowired
    private  CnicOcrService cnicOcrService;

    @Autowired
    private CnicParserService cnicParserService;

    @Autowired
    UserRepository userRepository;


    public CnicVerificationResponse submitVerifiction(String userId, MultipartFile frontImage, MultipartFile backImage){
        String frontUrl = s3UploadService.uploadFile(frontImage);
        String backUrl = s3UploadService.uploadFile(backImage);
        String extractedText = cnicOcrService.extractTextFromImage(frontUrl);
        String extractName = cnicParserService.extractedName(extractedText);

        String extractedCnic = cnicParserService.extractedCnicNumber(extractedText);

        // NOW CHECKING DUPLICATE CNIC NUMBER IN DATABASE
        Optional<CnicVerification> existingCnic = cnicVerificationRepository.findByExtractedCnicNumber(extractedCnic);
        if(existingCnic.isPresent()){
            throw new RuntimeException("CNIC already used");
        }

        // PREVENTS SAME USER FROM SUBMITTING MULTIPLE CNIC VERIFICATIONS
        Optional<CnicVerification> existingUser = cnicVerificationRepository.findByUserId(userId);
        if(existingUser.isPresent()){
            throw new RuntimeException("User has already submitted CNIC verification");
        }

        CnicVerification verification = CnicVerification.builder()
                .userId(userId)
                .frontImageUrl(frontUrl)
                .backImageUrl(backUrl)
                .extractedName(extractName)
                .extractedCnicNumber(extractedCnic)
                .status(CnicVerification.VerificationStatus.PENDING)
                .submittedAt(LocalDateTime.now())
                .build();

        cnicVerificationRepository.save(verification);

        CnicVerification savedVerification = cnicVerificationRepository.save(verification);
        return CnicVerificationResponse.builder()
                .id(savedVerification.getId())
                .extractedCnicNumber(extractedCnic)
                .extractedName(extractName)
                .frontImageUrl(frontUrl)
                .backImageUrl(backUrl)
                .status("PENDING")
                .build();
    }

    // ADMIN FUNCTION TO APPROVE OR REJECT CNIC VERIFICATION
    public CnicVerification approveVerification(String id){
        CnicVerification verification = cnicVerificationRepository.findByUserId(id).orElseThrow(() -> new RuntimeException("Verification not found"));
        verification.setStatus(CnicVerification.VerificationStatus.APPROVED);

        return cnicVerificationRepository.save(verification);
    }

    public CnicVerification rejectVerification(String id){
        CnicVerification verification = cnicVerificationRepository.findByUserId(id).orElseThrow(() -> new RuntimeException("Verification not found"));
        verification.setStatus(CnicVerification.VerificationStatus.REJECTED);

        return cnicVerificationRepository.save(verification);
    }

    // USER FUNCTION TO CHECK STATUS OF THEIR CNIC VERIFICATION
    public CnicVerification getMyVerificationStatus(Principal principal){
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return userRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Verification not found"));

    }

}
