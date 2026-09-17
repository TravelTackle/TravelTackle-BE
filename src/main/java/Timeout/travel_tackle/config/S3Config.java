package Timeout.travel_tackle.config;

import Timeout.travel_tackle.image.storage.ImageStorageProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

// aws.s3.bucket 이 비어 있으면 빈을 만들지 않아 로컬/테스트에서 AWS 없이 부팅된다
@Configuration
@ConditionalOnExpression("!'${aws.s3.bucket:}'.isBlank()")
public class S3Config {

    @Bean
    public ImageStorageProperties imageStorageProperties(
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.region:ap-northeast-2}") String region,
            @Value("${aws.s3.public-base-url:}") String publicBaseUrl
    ) {
        return new ImageStorageProperties(bucket, region, publicBaseUrl);
    }

    // 키가 있으면(로컬) 그 키를, 없으면(배포) IAM 역할 등 SDK 기본 체인을 쓴다
    @Bean
    public S3Client s3Client(
            ImageStorageProperties properties,
            @Value("${aws.credentials.access-key:}") String accessKey,
            @Value("${aws.credentials.secret-key:}") String secretKey
    ) {
        AwsCredentialsProvider credentials = accessKey.isBlank() || secretKey.isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials)
                .build();
    }
}
