package com.dmt.filesystem.controller;

import io.minio.*;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/files")
public class FileController {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    public FileController(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            // 원본 파일명에서 확장자 추출
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));

            // UUID로 새로운 파일명 생성
            String newFilename = UUID.randomUUID().toString() + extension;

            // 파일 업로드
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(newFilename)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // Presigned URL 생성 (1시간 유효)
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucketName)
                            .object(newFilename)
                            .method(Method.GET)
                            .expiry(60 * 60) // 1시간 (3600초) 유효
                            .build()
            );

            return ResponseEntity.ok("파일 업로드 성공! 다운로드 URL: " + presignedUrl);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("파일 업로드 실패: " + e.getMessage());
        }
    }

    // 📌 파일 다운로드 API (UUID 파일명 사용)
    @GetMapping("/download/{filename}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String filename) {
        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .build()
            );
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(stream.readAllBytes());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }


    @GetMapping("/list")
    public ResponseEntity<?> listFiles() {
        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );

            List<String> fileList = new ArrayList<>();
            for (Result<Item> result : objects) {
                fileList.add(result.get().objectName());
            }

            return ResponseEntity.ok(fileList);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("파일 리스트 조회 실패: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{filename}")
    public ResponseEntity<String> deleteFile(@PathVariable String filename) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .build()
            );
            return ResponseEntity.ok("파일 삭제 성공: " + filename);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("파일 삭제 실패: " + e.getMessage());
        }
    }

    @GetMapping("/url/{filename}")
    public ResponseEntity<String> getFileUrl(@PathVariable String filename) {
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .method(Method.GET)
                            .expiry(60 * 60) // 1시간 동안 유효
                            .build()
            );
            return ResponseEntity.ok(url);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("파일 URL 생성 실패: " + e.getMessage());
        }
    }
}