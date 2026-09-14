package Timeout.travel_tackle.auth.mail;

import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmtpVerificationMailSender implements VerificationMailSender {

    private final JavaMailSender javaMailSender;

    @Value("${app.mail.from:no-reply@travel-tackle.local}")
    private String from;

    @Value("${app.mail.from-name:트래블 참견}")
    private String fromName;

    // 만료 시간을 설정값에서 읽어 본문에 넣는다 — 하드코딩 문구가 실제 만료와 어긋나는 것을 방지
    @Value("${app.auth.email-verification-expiration-minutes:10}")
    private long verificationExpirationMinutes;

    @Value("${app.auth.password-reset-expiration-minutes:10}")
    private long passwordResetExpirationMinutes;

    @Override
    public void sendVerificationCode(String email, String code, String language) {
        String lang = normalizeLanguage(language);
        String html = buildHtml(
                lang,
                label(lang, Map.of("ko", "이메일 인증번호", "en", "Email Verification Code")),
                label(lang, Map.of("ko", "아래 인증번호를 인증 화면에 입력해 주세요.",
                        "en", "Enter the code below on the verification screen.")),
                code,
                verificationExpirationMinutes,
                null
        );
        String subject = label(lang, Map.of("ko", "[트래블 참견] 이메일 인증번호",
                "en", "[Travel Tackle] Email Verification Code"));
        send(email, subject, html, ErrorCode.EMAIL_DELIVERY_FAILED);
    }

    @Override
    public void sendPasswordResetCode(String email, String code, String language) {
        String lang = normalizeLanguage(language);
        String html = buildHtml(
                lang,
                label(lang, Map.of("ko", "비밀번호 재설정 인증번호", "en", "Password Reset Code")),
                label(lang, Map.of("ko", "아래 인증번호를 비밀번호 재설정 화면에 입력해 주세요.",
                        "en", "Enter the code below on the password reset screen.")),
                code,
                passwordResetExpirationMinutes,
                label(lang, Map.of("ko", "본인이 요청하지 않았다면 이 메일을 무시해 주세요.",
                        "en", "If you didn't request this, please ignore this email."))
        );
        String subject = label(lang, Map.of("ko", "[트래블 참견] 비밀번호 재설정 인증번호",
                "en", "[Travel Tackle] Password Reset Code"));
        send(email, subject, html, ErrorCode.PASSWORD_RESET_DELIVERY_FAILED);
    }

    /** 이 서비스의 기본 언어는 한국어 — "en"으로 명시된 경우에만 영어, 그 외(빈 값 포함)는 한국어. */
    private String normalizeLanguage(String language) {
        return "en".equalsIgnoreCase(language) ? "en" : "ko";
    }

    private String label(String lang, Map<String, String> labels) {
        return labels.getOrDefault(lang, labels.get("en"));
    }

    private void send(String to, String subject, String html, ErrorCode failureCode) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.addInline("logo", new ClassPathResource("mail/logo.png"), "image/png");
            javaMailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException exception) {
            log.error(
                    "SMTP email delivery failed: exceptionType={}, reason={}",
                    exception.getClass().getSimpleName(),
                    sanitize(exception.getMessage())
            );
            throw new CustomException(failureCode);
        }
    }

    // 디자인 토큰(브랜드 #2563EB / 다크 #1D4ED8 / 라이트배경 #EFF6FF / 텍스트 #0F172A)은
    // 프론트엔드와 동일하게 맞춘 것 — 프론트 디자인이 바뀌면 이 값도 함께 갱신
    private String buildHtml(String lang, String title, String intro, String code, long minutes, String extraNote) {
        String noteLine = extraNote == null ? "" : "<br>" + extraNote;
        String expiryTemplate = label(lang, Map.of(
                "ko", "%d분 안에 입력해 주세요.",
                "en", "Please enter it within %d minutes."));
        String expiryLine = expiryTemplate.formatted(minutes);
        String brandName = label(lang, Map.of("ko", "트래블 참견", "en", "Travel Tackle"));
        return """
                <!DOCTYPE html>
                <html lang="%s">
                <head>
                <meta charset="UTF-8">
                <meta name="color-scheme" content="light">
                <meta name="supported-color-schemes" content="light">
                <style>
                  :root { color-scheme: light only; supported-color-schemes: light; }
                </style>
                </head>
                <body style="margin:0;padding:0;background-color:#EFF6FF;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Apple SD Gothic Neo','Malgun Gothic',sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" bgcolor="#EFF6FF" style="background-color:#EFF6FF;padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="480" cellpadding="0" cellspacing="0" bgcolor="#FFFFFE" style="max-width:480px;width:100%%;background-color:#FFFFFE;border-radius:8px;box-shadow:0 4px 16px rgba(15,23,42,.05);overflow:hidden;">
                          <tr>
                            <td style="padding:28px 32px 20px 32px;text-align:center;">
                              <img src="cid:logo" width="160" height="40" alt="%s" style="display:block;margin:0 auto;">
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 32px 32px 32px;text-align:center;">
                              <h1 style="margin:0 0 12px 0;font-size:18px;font-weight:700;color:#0F172A;">%s</h1>
                              <p style="margin:0 0 24px 0;font-size:14px;line-height:1.6;color:#475569;">%s</p>
                              <div style="text-align:center;background-color:#EFF6FF;border-radius:8px;padding:20px;margin-bottom:24px;">
                                <span style="font-size:32px;font-weight:800;letter-spacing:8px;color:#2563EB;">%s</span>
                              </div>
                              <p style="margin:0;font-size:13px;line-height:1.6;color:#64748B;">%s%s</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:20px 32px;background-color:#EFF6FF;text-align:center;">
                              <p style="margin:0;font-size:12px;color:#94A3B8;">&copy; %s</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(lang, brandName, title, intro, code, expiryLine, noteLine, brandName);
    }

    private String sanitize(String message) {
        if (message == null) {
            return "unknown";
        }
        return message.replaceAll("(?i)(password|passwd|pwd)=[^,;\\s]+", "$1=***");
    }
}
