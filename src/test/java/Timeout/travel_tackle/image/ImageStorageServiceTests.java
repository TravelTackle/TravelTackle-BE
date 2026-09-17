package Timeout.travel_tackle.image;

import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.image.service.ImageStorageService;
import Timeout.travel_tackle.image.storage.ImageStorageProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageStorageServiceTests {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0};

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BASE = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/";

    private final ImageStorageProperties properties =
            new ImageStorageProperties("test-bucket", "ap-northeast-2", "");
    private final S3Client s3Client = mock(S3Client.class);

    @Test
    void uploadsToServerGeneratedKeyAndReturnsPublicUrl() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        ImageStorageService service = service(s3Client, properties);

        String url = service.upload(USER_ID, new MockMultipartFile("photos", "my photo.JPG", "image/jpeg", JPEG));

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest sent = captor.getValue();
        assertEquals("test-bucket", sent.bucket());
        assertTrue(sent.key().startsWith("images/" + USER_ID + "/"));
        assertTrue(sent.key().endsWith(".jpg"));
        assertEquals("image/jpeg", sent.contentType());
        assertEquals((long) JPEG.length, sent.contentLength());
        assertEquals(BASE + sent.key(), url);
    }

    @Test
    void typeComesFromFileSignatureNotDeclaredContentType() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        ImageStorageService service = service(s3Client,
                new ImageStorageProperties("test-bucket", "ap-northeast-2", "https://cdn.example.com/"));

        // 헤더는 jpeg 라고 하지만 실제 바이트는 PNG
        String url = service.upload(USER_ID, new MockMultipartFile("photos", "a.jpg", "image/jpeg", PNG));

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertEquals("image/png", captor.getValue().contentType());
        assertTrue(url.startsWith("https://cdn.example.com/images/"));
        assertTrue(url.endsWith(".png"));
    }

    @Test
    void rejectsNonImageBytesEmptyAndOversizedFiles() {
        ImageStorageService service = service(s3Client, properties);

        assertEquals(ErrorCode.UNSUPPORTED_IMAGE_TYPE, assertThrows(CustomException.class, () ->
                service.upload(USER_ID, new MockMultipartFile("photos", "a.jpg", "image/jpeg", "hello".getBytes())))
                .getErrorCode());
        assertEquals(ErrorCode.UNSUPPORTED_IMAGE_TYPE, assertThrows(CustomException.class, () ->
                service.validate(new MockMultipartFile("photos", "a.gif", "image/gif", new byte[]{'G', 'I', 'F', '8'})))
                .getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(CustomException.class, () ->
                service.upload(USER_ID, new MockMultipartFile("photos", "a.png", "image/png", new byte[0])))
                .getErrorCode());
        byte[] huge = new byte[(int) ImageStorageService.MAX_CONTENT_LENGTH + 1];
        System.arraycopy(PNG, 0, huge, 0, PNG.length);
        assertEquals(ErrorCode.IMAGE_TOO_LARGE, assertThrows(CustomException.class, () ->
                service.upload(USER_ID, new MockMultipartFile("photos", "a.png", "image/png", huge)))
                .getErrorCode());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void validateAcceptsWebpAndWorksWithoutStorageConfigured() {
        ImageStorageService service = service(null, null);

        service.validate(new MockMultipartFile("photos", "a.webp", "image/webp", WEBP));
        CustomException ex = assertThrows(CustomException.class, () ->
                service.upload(USER_ID, new MockMultipartFile("photos", "a.png", "image/png", PNG)));
        assertEquals(ErrorCode.IMAGE_STORAGE_NOT_CONFIGURED, ex.getErrorCode());
        service.deleteQuietly(USER_ID, BASE + "images/" + USER_ID + "/x.png"); // no-op, no throw
    }

    @Test
    void deletesOnlyOwnObjectsInOurBucketAndSwallowsFailures() {
        ImageStorageService service = service(s3Client, properties);

        service.deleteQuietly(USER_ID, "https://picsum.photos/seed/x/800/600");
        service.deleteQuietly(USER_ID, BASE + "images/" + OTHER_ID + "/2026/09/a.jpg"); // 다른 사용자의 객체
        service.deleteQuietly(USER_ID, BASE + "other-prefix/a.jpg");
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));

        service.deleteQuietly(USER_ID, BASE + "images/" + USER_ID + "/2026/09/a.jpg");
        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertEquals("images/" + USER_ID + "/2026/09/a.jpg", captor.getValue().key());

        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(new RuntimeException("boom"));
        service.deleteQuietly(USER_ID, BASE + "images/" + USER_ID + "/2026/09/b.jpg");
    }

    @Test
    void publicBaseUrlWithoutSchemeGetsHttps() {
        ImageStorageProperties bare = new ImageStorageProperties("b", "ap-northeast-2", "dxxx.cloudfront.net/");
        assertEquals("https://dxxx.cloudfront.net/images/a.jpg", bare.publicUrlOf("images/a.jpg"));
        assertEquals("images/a.jpg", bare.keyOf("https://dxxx.cloudfront.net/images/a.jpg"));
    }

    @Test
    void wrapsS3FailuresInAClearErrorCode() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(301).message("wrong endpoint").build());
        ImageStorageService service = service(s3Client, properties);

        CustomException ex = assertThrows(CustomException.class, () ->
                service.upload(USER_ID, new MockMultipartFile("photos", "a.jpg", "image/jpeg", JPEG)));
        assertEquals(ErrorCode.IMAGE_UPLOAD_FAILED, ex.getErrorCode());
    }

    @Test
    void profileImagesUseTheirOwnPrefixAndRecordCleanupCannotDeleteThem() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        ImageStorageService service = service(s3Client, properties);

        String url = service.upload(USER_ID, new MockMultipartFile("image", "me.png", "image/png", PNG), ImageStorageService.Kind.PROFILE);
        assertTrue(url.startsWith(BASE + "profiles/" + USER_ID + "/"));

        service.deleteQuietly(USER_ID, url); // 기록 정리 경로(RECORD)는 프로필 키를 건드리지 않는다
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        service.deleteQuietly(USER_ID, url, ImageStorageService.Kind.PROFILE);
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    private static ImageStorageService service(S3Client s3Client, ImageStorageProperties properties) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (s3Client != null) {
            beans.addBean("s3Client", s3Client);
        }
        if (properties != null) {
            beans.addBean("properties", properties);
        }
        return new ImageStorageService(
                beans.getBeanProvider(S3Client.class),
                beans.getBeanProvider(ImageStorageProperties.class));
    }
}
