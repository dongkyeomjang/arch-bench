package com.example.fixtures;

import com.example.lending.architecture.HexagonalRules;
import com.example.lending.architecture.HexagonalRules.NamedRule;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * 음성 검증: 각 픽스처는 기대 집합에 속한 규칙만 깨고(AssertionError), 나머지 규칙은 통과해야 한다(오탐 없음).
 * 위반 건수(getFailureReport().getDetails().size())도 고정해, 규칙 일부만 느슨해져도 감지되게 한다.
 *
 * <pre>
 * | 픽스처 | 기대 집합 (규칙: 건수) | 이유                                                                  |
 * |--------|------------------------|-----------------------------------------------------------------------|
 * | v1     | R1: 3                  | domain.Book -> adapter.out.persistence (adapter는 domain을 참조 안 함) |
 * | v2     | R2: 5                  | application.service -> adapter.out.persistence (역방향 없음)           |
 * | v3     | R3: 7                  | web -> persistence 4건 + persistence -> web 3건 (같은 adapter 슬라이스) |
 * | v4     | R1: 2, R4: 1           | domain -> application은 R1 위반이자 domain<->application 순환          |
 * | v5     | R1: 3, R5: 5           | @Entity/@Component/@Service는 R1이 금지한 패키지에 대한 의존이기도 함  |
 * | v6     | R6: 2                  | abstract class 포트 1 + application.service에 있는 port.out 구현체 1   |
 * </pre>
 */
@Tag("arch")
class ViolationDetectionTest {

    static Stream<Arguments> fixtures() {
        return Stream.of(
                arguments("v1", Map.of("R1", 3)),
                arguments("v2", Map.of("R2", 5)),
                arguments("v3", Map.of("R3", 7)),
                arguments("v4", Map.of("R1", 2, "R4", 1)),
                arguments("v5", Map.of("R1", 3, "R5", 5)),
                arguments("v6", Map.of("R6", 2)));
    }

    @ParameterizedTest(name = "{0} violates exactly {1}")
    @MethodSource("fixtures")
    void fixture_violates_exactly_the_expected_rules(String fixture, Map<String, Integer> expected) {
        String basePackage = "com.example.fixtures." + fixture;
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new DoNotIncludeTests())
                .importPackages(basePackage);
        assertFalse(classes.isEmpty(), () -> "no classes imported for " + basePackage);

        List<String> mismatches = new ArrayList<>();
        StringBuilder row = new StringBuilder("[matrix] " + fixture);
        for (NamedRule namedRule : HexagonalRules.forBasePackage(basePackage)) {
            boolean threw;
            try {
                namedRule.rule().check(classes);
                threw = false;
            } catch (AssertionError e) {
                threw = true;
            }
            List<String> details = namedRule.rule().evaluate(classes).getFailureReport().getDetails();
            row.append(" | ").append(namedRule.id()).append('=').append(threw ? "FAIL(" + details.size() + ")" : "pass");

            boolean shouldThrow = expected.containsKey(namedRule.id());
            if (shouldThrow && !threw) {
                mismatches.add(fixture + " " + namedRule.id() + ": expected violation but rule passed (missed)");
            } else if (!shouldThrow && threw) {
                mismatches.add(fixture + " " + namedRule.id() + ": unexpected violation (false positive)\n  "
                        + String.join("\n  ", details));
            } else if (shouldThrow && details.size() != expected.get(namedRule.id())) {
                mismatches.add(fixture + " " + namedRule.id() + ": expected " + expected.get(namedRule.id())
                        + " violations but got " + details.size() + "\n  " + String.join("\n  ", details));
            }
        }
        System.out.println(row);

        assertTrue(mismatches.isEmpty(), () -> String.join("\n", mismatches));
    }
}
