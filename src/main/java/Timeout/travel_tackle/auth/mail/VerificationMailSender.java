package Timeout.travel_tackle.auth.mail;

public interface VerificationMailSender {

    void sendVerificationCode(String email, String code, String language);

    void sendPasswordResetCode(String email, String code, String language);
}
