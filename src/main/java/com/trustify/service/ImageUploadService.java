package com.trustify.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private final S3UploadService s3Service;

    public String saveImage(MultipartFile file) {
        return s3Service.uploadFile(file);
    }
}
