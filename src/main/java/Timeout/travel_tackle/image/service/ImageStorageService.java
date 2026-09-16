package Timeout.travel_tackle.image.service;

import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.image.storage.ImageStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageStorageService {

    public static final long MAX_CONTENT_LENGTH = 10L * 1024 * 1024;

    /** 이미지 용도. 키 접두사가 다르고, 삭제도 같은 용도의 키만 지운다 (기록 정리가 프로필 사진을 지우지 못하게). */
    public enum Kind {
        RECORD("images/"),
        PROFILE("profiles/");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }
    }
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF_MAGIC = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_MAGIC = {'W', 'E', 'B', 'P'};

    private record ImageType(String contentType, String extension) {
    }

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final ObjectProvider<ImageStorageProperties> propertiesProvider;

    /** 업로드 전에 모든 파일을 먼저 검사할 수 있도록 검증만 분리. 형식은 헤더가 아니라 파일 시그니처로 판단한다. */
    public void validate(MultipartFile file) {
        detectType(file);
    }

    /** 파일을 S3 에 올리고 읽기 URL 을 돌려준다 (기록 사진). */
    public String upload(UUID userId, MultipartFile file) {
        return upload(userId, file, Kind.RECORD);
    }

    public String upload(UUID userId, MultipartFile file, Kind kind) {
        ImageStorageProperties properties = propertiesProvider.getIfAvailable();
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (properties == null || s3Client == null) {
            throw new CustomException(ErrorCode.IMAGE_STORAGE_NOT_CONFIGURED);
        }
        ImageType type = detectType(file);

        String key = buildKey(userId, type.extension(), kind);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(type.contentType())
                .contentLength(file.getSize())
                .build();
        // 재시도 시 스트림을 다시 열 수 있게 supplier 로 넘긴다
        ContentStreamProvider provider = ContentStreamProvider.fromInputStreamSupplier(() -> {
            try {
                return file.getInputStream();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        try {
            s3Client.putObject(request, RequestBody.fromContentProvider(provider, file.getSize(), type.contentType()));
        } catch (SdkException e) {
            // 리전 불일치(301), 자격 증명 오류(403), 버킷 없음(404) 등은 설정 문제라 500 대신 명확한 코드로 돌려준다
            log.error("S3 업로드 실패 (bucket={}, region={}, key={})", properties.bucket(), properties.region(), key, e);
            throw new CustomException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
        return properties.publicUrlOf(key);
    }

    /**
     * ownerId 가 올린 객체(images/{ownerId}/...)만 지운다. 외부 URL 이나 다른 사용자 키는 무시하고,
     * 실패해도 호출자 흐름은 막지 않는다.
     */
    public void deleteQuietly(UUID ownerId, String imageUrl) {
        deleteQuietly(ownerId, imageUrl, Kind.RECORD);
    }

    public void deleteQuietly(UUID ownerId, String imageUrl, Kind kind) {
        ImageStorageProperties properties = propertiesProvider.getIfAvailable();
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (properties == null || s3Client == null) {
            return;
        }
        String key = properties.keyOf(imageUrl);
        if (key == null || !key.startsWith(kind.prefix + ownerId + "/")) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
        } catch (RuntimeException e) {
            log.warn("S3 객체 삭제 실패 (key={})", key, e);
        }
    }

    /** DB 커밋이 실패했는데 S3 객체만 사라지는 일이 없도록, 트랜잭션 안에서는 커밋 이후에 지운다. */
    public void deleteAfterCommit(UUID ownerId, List<String> imageUrls) {
        deleteAfterCommit(ownerId, imageUrls, Kind.RECORD);
    }

    public void deleteAfterCommit(UUID ownerId, List<String> imageUrls, Kind kind) {
        List<String> urls = List.copyOf(imageUrls);
        if (urls.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            urls.forEach(url -> deleteQuietly(ownerId, url, kind));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                urls.forEach(url -> deleteQuietly(ownerId, url, kind));
            }
        });
    }

    /** 업로드는 트랜잭션 밖(S3)에서 일어나므로, 롤백되면 방금 올린 객체를 되돌려 지운다. */
    public void deleteOnRollback(UUID ownerId, List<String> imageUrls) {
        deleteOnRollback(ownerId, imageUrls, Kind.RECORD);
    }

    public void deleteOnRollback(UUID ownerId, List<String> imageUrls, Kind kind) {
        List<String> urls = List.copyOf(imageUrls);
        if (urls.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    urls.forEach(url -> deleteQuietly(ownerId, url, kind));
                }
            }
        });
    }

    private ImageType detectType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (file.getSize() > MAX_CONTENT_LENGTH) {
            throw new CustomException(ErrorCode.IMAGE_TOO_LARGE);
        }
        byte[] head = readHead(file, 12);
        if (startsWith(head, 0, JPEG_MAGIC)) {
            return new ImageType("image/jpeg", "jpg");
        }
        if (startsWith(head, 0, PNG_MAGIC)) {
            return new ImageType("image/png", "png");
        }
        if (startsWith(head, 0, RIFF_MAGIC) && startsWith(head, 8, WEBP_MAGIC)) {
            return new ImageType("image/webp", "webp");
        }
        throw new CustomException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    private static byte[] readHead(MultipartFile file, int length) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(length);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private static boolean startsWith(byte[] data, int offset, byte[] magic) {
        return data.length >= offset + magic.length
                && Arrays.equals(data, offset, offset + magic.length, magic, 0, magic.length);
    }

    // 클라이언트 파일명은 쓰지 않는다 — 경로 조작·중복을 막기 위해 서버가 키를 정하고, 소유자 ID 를 키에 넣어 삭제 권한의 근거로 쓴다
    private String buildKey(UUID userId, String extension, Kind kind) {
        LocalDate today = LocalDate.now();
        return kind.prefix + userId + "/" + today.getYear() + "/" + String.format("%02d", today.getMonthValue())
                + "/" + UUID.randomUUID() + "." + extension;
    }
}
