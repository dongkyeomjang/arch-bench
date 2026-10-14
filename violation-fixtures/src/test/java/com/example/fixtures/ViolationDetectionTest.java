package com.example.fixtures;

import com.example.lending.architecture.HexagonalRules;
import com.example.lending.architecture.HexagonalRules.NamedRule;
import com.example.lending.architecture.ViolationExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * 음성 검증: 각 픽스처는 기대 집합에 속한 규칙만 깨고(AssertionError), 나머지 규칙은 통과해야 한다(오탐 없음).
 * 위반 건수와 원인 클래스 집합(ViolationExtractor)도 고정한다. 건수만 맞고 클래스가 틀리면 추출기 버그다.
 * 주 지표(violating_classes = PRIMARY_RULES R1·R2·R3·R6·R7 원인 클래스 합집합)도 픽스처별로 고정한다.
 * 클래스명은 픽스처 base 패키지 기준 상대 이름이다.
 *
 * <pre>
 * | 픽스처 | 기대 (규칙: 건수 [원인 클래스])                                                      | violating_classes |
 * |--------|--------------------------------------------------------------------------------------|-------------------|
 * | v1     | R1: 3 [domain.Book]                                                                  | 1                 |
 * | v2     | R2: 5 [application.service.LoanBookService]                                          | 1                 |
 * | v3     | R3: 7 [adapter.in.web.LoanController, adapter.out.persistence.BookJpaEntity]         | 2                 |
 * | v4     | R1: 2 [domain.Book] / R4: 1 [application.service.LoanPolicy, domain.Book]            | 1 (R4 제외 → LoanPolicy 빠짐) |
 * | v5     | R1: 9 [domain.Book, domain.Loan, domain.Member] / R5: 5 [같은 3개]                   | 3 (= R1 원인 클래스) |
 * | v6     | R6: 2 [application.port.out.LoadBookPort, application.service.InMemoryBookStore]     | 2                 |
 * | v7     | R7: 2 [AppConstants, common.DateUtils]                                               | 2                 |
 * | v8     | R1: 1 [domain.Member]                                                                | 1                 |
 * </pre>
 * v5 R1이 9건인 이유: 클래스 어노테이션 3건 + @JsonProperty 2곳 × 3건(어노테이션 자체, 멤버 기본값 타입
 * JsonProperty$Access, OptBoolean). R1에 com.fasterxml.jackson..이 추가된 2차 변경 전에는 3건이었다.
 */
@Tag("arch")
class ViolationDetectionTest {

    private record Expected(int count, Set<String> classes) {
    }

    private static Expected expect(int count, String... relativeClassNames) {
        return new Expected(count, Set.of(relativeClassNames));
    }

    private static Set<String> primary(String... relativeClassNames) {
        return Set.of(relativeClassNames);
    }

    static Stream<Arguments> fixtures() {
        return Stream.of(
                arguments("v1", Map.of("R1", expect(3, "domain.Book")),
                        primary("domain.Book")),
                arguments("v2", Map.of("R2", expect(5, "application.service.LoanBookService")),
                        primary("application.service.LoanBookService")),
                arguments("v3", Map.of("R3", expect(7, "adapter.in.web.LoanController", "adapter.out.persistence.BookJpaEntity")),
                        primary("adapter.in.web.LoanController", "adapter.out.persistence.BookJpaEntity")),
                arguments("v4", Map.of(
                                "R1", expect(2, "domain.Book"),
                                "R4", expect(1, "application.service.LoanPolicy", "domain.Book")),
                        primary("domain.Book")),
                arguments("v5", Map.of(
                                "R1", expect(9, "domain.Book", "domain.Loan", "domain.Member"),
                                "R5", expect(5, "domain.Book", "domain.Loan", "domain.Member")),
                        primary("domain.Book", "domain.Loan", "domain.Member")),
                arguments("v6", Map.of("R6", expect(2, "application.port.out.LoadBookPort", "application.service.InMemoryBookStore")),
                        primary("application.port.out.LoadBookPort", "application.service.InMemoryBookStore")),
                arguments("v7", Map.of("R7", expect(2, "AppConstants", "common.DateUtils")),
                        primary("AppConstants", "common.DateUtils")),
                arguments("v8", Map.of("R1", expect(1, "domain.Member")),
                        primary("domain.Member")));
    }

    @ParameterizedTest(name = "{0} violates exactly {1}, violating_classes {2}")
    @MethodSource("fixtures")
    void fixture_violates_exactly_the_expected_rules(String fixture, Map<String, Expected> expected,
                                                     Set<String> expectedPrimaryClasses) throws Exception {
        String basePackage = "com.example.fixtures." + fixture;
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new DoNotIncludeTests())
                .importPackages(basePackage);
        assertFalse(classes.isEmpty(), () -> "no classes imported for " + basePackage);

        List<String> mismatches = new ArrayList<>();
        Map<String, EvaluationResult> results = new LinkedHashMap<>();
        StringBuilder row = new StringBuilder("[matrix] " + fixture);
        for (NamedRule namedRule : HexagonalRules.forBasePackage(basePackage)) {
            String ruleId = namedRule.id();
            boolean threw;
            try {
                namedRule.rule().check(classes);
                threw = false;
            } catch (AssertionError e) {
                threw = true;
            }
            EvaluationResult result = namedRule.rule().evaluate(classes);
            results.put(ruleId, result);
            List<String> details = result.getFailureReport().getDetails();
            SortedSet<String> causes = ViolationExtractor.violatingClasses(result).stream()
                    .map(name -> name.substring(basePackage.length() + 1))
                    .collect(Collectors.toCollection(TreeSet::new));
            row.append(" | ").append(ruleId).append('=')
                    .append(threw ? "FAIL(" + details.size() + ")" + causes : "pass");

            Expected expectation = expected.get(ruleId);
            if (expectation != null && !threw) {
                mismatches.add(fixture + " " + ruleId + ": expected violation but rule passed (missed)");
            } else if (expectation == null && threw) {
                mismatches.add(fixture + " " + ruleId + ": unexpected violation (false positive)\n  "
                        + String.join("\n  ", details));
            } else if (expectation != null && details.size() != expectation.count()) {
                mismatches.add(fixture + " " + ruleId + ": expected " + expectation.count()
                        + " violations but got " + details.size() + "\n  " + String.join("\n  ", details));
            } else if (expectation != null && !causes.equals(new TreeSet<>(expectation.classes()))) {
                mismatches.add(fixture + " " + ruleId + ": expected cause classes " + new TreeSet<>(expectation.classes())
                        + " but extracted " + causes + "\n  " + String.join("\n  ", details));
            }
        }
        Map<String, Object> report = ViolationExtractor.report(results);
        if (!List.of("R1", "R2", "R3", "R6", "R7").equals(report.get("primary_rules"))) {
            mismatches.add(fixture + ": primary_rules expected [R1, R2, R3, R6, R7] but was " + report.get("primary_rules"));
        }
        SortedSet<String> primaryClasses = ((List<?>) report.get("violating_classes_list")).stream()
                .map(name -> ((String) name).substring(basePackage.length() + 1))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!primaryClasses.equals(new TreeSet<>(expectedPrimaryClasses))
                || (int) report.get("violating_classes") != expectedPrimaryClasses.size()) {
            mismatches.add(fixture + ": expected violating_classes " + expectedPrimaryClasses.size() + " "
                    + new TreeSet<>(expectedPrimaryClasses) + " but got " + report.get("violating_classes") + " " + primaryClasses);
        }
        row.append(" || violating_classes=").append(report.get("violating_classes")).append(primaryClasses);
        System.out.println(row);
        System.out.println("[json] " + fixture + " " + new ObjectMapper().writeValueAsString(report));

        assertTrue(mismatches.isEmpty(), () -> String.join("\n", mismatches));
    }
}
