package Timeout.travel_tackle.entity.Enum;

public enum AuthProvider {
    LOCAL,  // 이메일과 비밀번호를 사용하는 일반 계정
    KAKAO,  // 카카오 계정
    GOOGLE, // 구글 계정
    APPLE   // 애플 계정 (연동 미구현, 값만 선점)
}
