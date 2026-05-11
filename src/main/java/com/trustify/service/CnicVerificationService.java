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
import java.util.List;
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
        // CHECK IF USER ALREADY HAS VERIFICATION
        Optional<CnicVerification> existingVerification =
                cnicVerificationRepository.findByUserId(userId);

        if(existingVerification.isPresent()){

            CnicVerification.VerificationStatus status =
                    existingVerification.get().getStatus();

            if(status == CnicVerification.VerificationStatus.PENDING ||
                    status == CnicVerification.VerificationStatus.APPROVED){

                throw new RuntimeException(
                        "Verification already submitted"
                );
            }
        }


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

        CnicVerification verification = CnicVerification.builder()
                .userId(userId)
                .frontImageUrl(frontUrl)
                .backImageUrl(backUrl)
                .extractedName(extractName)
                .extractedCnicNumber(extractedCnic)
                .status(CnicVerification.VerificationStatus.PENDING)
                .submittedAt(LocalDateTime.now())
                .build();

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

        // FIND USER AND UPDATE THEIR FRAUD SCORE OR TRUST RATING BASED ON APPROVAL
        User user = userRepository.findById(verification.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        // MARK USER AS VERIFIED IN USER PROFILE
        user.setVerified(true);

        // OPTIONAL: REWARD USER FOR SUCCESSFUL VERIFICATION
        user.setTrustRating(Math.max(5.0,user.getTrustRating() + 20)); // Increase trust rating by 20 points for successful verification, max 100.

        userRepository.save(user);

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

        return cnicVerificationRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Verification not found"));

    }

    // ADMIN GETS ALL PENDING VERIFICATIONS
    public List<CnicVerification> getPendingVerifications(){
        return cnicVerificationRepository.findByStatus(CnicVerification.VerificationStatus.PENDING);
    }

    // GET ALL VERIFICATIONS FOR ADMIN
    public List<CnicVerification> getAllVerifications(){
        return cnicVerificationRepository.findAll();
    }

}
