package Timeout.travel_tackle.image.storage;

public record ImageStorageProperties(
        String bucket,
        String region,
        String publicBaseUrl // CloudFront 등 읽기용 도메인. 비어 있으면 S3 가상 호스팅 URL 사용
) {
    public String baseUrl() {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return "https://" + bucket + ".s3." + region + ".amazonaws.com";
        }
        String base = publicBaseUrl.trim().replaceAll("/+$", "");
        // 스킴 없이 도메인만 넣어도(dxxx.cloudfront.net) 절대 URL 이 되도록 보정
        return base.matches("(?i)^https?://.*") ? base : "https://" + base;
    }

    public String publicUrlOf(String key) {
        return baseUrl() + "/" + key;
    }

    /** 우리 저장소 URL 이면 객체 키를, 아니면(외부 URL) null 을 돌려준다. */
    public String keyOf(String imageUrl) {
        String prefix = baseUrl() + "/";
        return imageUrl != null && imageUrl.startsWith(prefix) ? imageUrl.substring(prefix.length()) : null;
    }
}
