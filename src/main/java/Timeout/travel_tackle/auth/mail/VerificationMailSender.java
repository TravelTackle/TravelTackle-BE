package Timeout.travel_tackle.auth.mail;

public interface VerificationMailSender {

    void sendVerificationCode(String email, String code);

    void sendPasswordResetCode(String email, String code);
}
