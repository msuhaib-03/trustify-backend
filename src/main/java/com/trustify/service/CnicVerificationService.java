package com.trustify.service;

import com.trustify.dto.CnicVerificationResponse;
import com.trustify.model.CnicVerification;
import com.trustify.repository.CnicVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

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


    public CnicVerificationResponse submitVerifiction(String userId, MultipartFile frontImage, MultipartFile backImage){
        String frontUrl = s3UploadService.uploadFile(frontImage);
        String backUrl = s3UploadService.uploadFile(backImage);
        String extractedText = cnicOcrService.extractTextFromImage(frontUrl);
        String extractName = cnicParserService.extractedName(extractedText);

        String extractedCnic = cnicParserService.extractedCnicNumber(extractedText);

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

}
