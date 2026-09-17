package Timeout.travel_tackle.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 아키텍처 규칙 (ArchUnit) — AI 생성 코드 가드레일.
 * 위반이 나오면 그 메시지를 근거로 코드를 규칙에 맞게 수정한다(생성→검사→수정 루프).
 *
 * 규칙 개요:
 *  1) 어노테이션 ↔ 클래스 종류 일관성: @Service/@Repository/@Controller 는 각각 맞는 클래스에만.
 *  2) 컨트롤러/서비스/리포지토리는 각자 분리된 패키지(..controller../..service../..repository..)에 위치.
 *  3) 네이밍: 클래스 PascalCase(밑줄 금지), 메서드 camelCase.
 *  4) 객체지향/설계: 기능 패키지 간 순환 의존 금지, 컨트롤러의 리포지토리 직접 참조 금지.
 *
 * 참고: ArchUnit 의 JUnit5 엔진(@ArchTest)은 JUnit Platform 6 과 호환되지 않으므로(1.4.2 확인),
 *      자체 엔진 대신 표준 Jupiter @Test 에서 rule.check() 를 호출한다.
 */
class ArchitectureTest {

    private static final String BASE = "Timeout.travel_tackle";
    private static JavaClasses classes;

    @BeforeAll
    static void importMainClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    // ── 1) 어노테이션이 올바른 종류의 클래스에만 ─────────────────

    @Test
    void 서비스_어노테이션은_서비스_클래스에만() {
        classes().that().areAnnotatedWith(Service.class)
                .should().haveSimpleNameEndingWith("Service")
                .because("@Service 는 서비스 클래스(*Service)에만 사용해야 합니다")
                .check(classes);
    }

    @Test
    void 리포지토리_어노테이션은_리포지토리_클래스에만() {
        classes().that().areAnnotatedWith(Repository.class)
                .should().haveSimpleNameEndingWith("Repository")
                .because("@Repository 는 리포지토리 클래스(*Repository)에만 사용해야 합니다")
                .check(classes);
    }

    @Test
    void 컨트롤러_어노테이션은_컨트롤러_클래스에만() {
        classes().that().areAnnotatedWith(Controller.class).or().areAnnotatedWith(RestController.class)
                .should().haveSimpleNameEndingWith("Controller")
                .because("@Controller/@RestController 는 컨트롤러 클래스(*Controller)에만 사용해야 합니다")
                .check(classes);
    }

    // ── 2) 이름이 종류를 뜻하면 알맞은 어노테이션 + 분리된 패키지 ──

    @Test
    void 서비스는_서비스어노테이션과_서비스패키지() {
        classes().that().haveSimpleNameEndingWith("Service")
                // 스프링 시큐리티 SPI 구현체 예외: BearerTokenResolver 구현이라 @Service 빈이 아니며
                // 시큐리티 인프라(auth.jwt)에 위치한다.
                .and().doNotHaveFullyQualifiedName("Timeout.travel_tackle.auth.jwt.AuthCookieService")
                .should().beAnnotatedWith(Service.class)
                .andShould().resideInAPackage("..service..")
                .because("서비스는 @Service 를 달고 분리된 ..service.. 패키지에 있어야 합니다")
                .check(classes);
    }

    @Test
    void 컨트롤러는_컨트롤러어노테이션과_컨트롤러패키지() {
        classes().that().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith(RestController.class).orShould().beAnnotatedWith(Controller.class)
                .andShould().resideInAPackage("..controller..")
                .because("컨트롤러는 @Controller/@RestController 를 달고 분리된 ..controller.. 패키지에 있어야 합니다")
                .check(classes);
    }

    @Test
    void 리포지토리는_리포지토리패키지() {
        classes().that().haveSimpleNameEndingWith("Repository")
                // 스프링 시큐리티 SPI 구현체 예외: AuthorizationRequestRepository 구현이라
                // 데이터 리포지토리가 아니며 시큐리티 인프라(auth.social)에 위치한다.
                .and().doNotHaveFullyQualifiedName(
                        "Timeout.travel_tackle.auth.social.SignedCookieOAuth2AuthorizationRequestRepository")
                .should().resideInAPackage("..repository..")
                .because("리포지토리는 분리된 ..repository.. 패키지에 있어야 합니다")
                .check(classes);
    }

    // ── 3) 네이밍 규칙 ───────────────────────────────────────

    @Test
    void 클래스는_PascalCase_밑줄금지() {
        classes().should().haveSimpleNameNotContaining("_")
                .andShould().haveNameMatching(".*\\.[A-Z][A-Za-z0-9$]*")
                .because("클래스명은 밑줄 없는 PascalCase 여야 합니다")
                .check(classes);
    }

    @Test
    void 메서드는_camelCase() {
        // 컴파일러가 enum 등에 자동 생성하는 합성 메서드($values, lambda$... 등)는 제외
        methods().that().doNotHaveModifier(JavaModifier.SYNTHETIC)
                .should().haveNameMatching("[a-z][A-Za-z0-9]*")
                .because("메서드명은 camelCase 여야 합니다")
                .check(classes);
    }

    // ── 4) 객체지향 / 설계원칙 ───────────────────────────────

    @Test
    void 기능패키지간_순환의존_금지() {
        slices().matching(BASE + ".(*)..")
                .should().beFreeOfCycles()
                .because("기능(feature) 패키지 사이에 순환 의존이 없어야 합니다")
                .check(classes);
    }

    @Test
    void 컨트롤러는_리포지토리를_직접참조_금지() {
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .because("컨트롤러는 서비스를 거쳐야 하며 리포지토리를 직접 참조하면 안 됩니다")
                .check(classes);
    }
}
