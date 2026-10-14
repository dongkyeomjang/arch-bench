package com.example.lending.architecture;

import com.example.lending.architecture.HexagonalRules.NamedRule;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTag;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 양성 검증: 올바른 참조 구현(lending)에서 R1~R6이 모두 통과해야 한다.
 * 실패 여부와 관계없이 규칙별 위반 건수를 build/arch/lending.json에 먼저 기록한다(실험 파이프라인 측정값).
 */
@Tag("arch")
@ArchTag("arch")
@AnalyzeClasses(packages = ArchitectureTest.BASE_PACKAGE, importOptions = DoNotIncludeTests.class)
class ArchitectureTest {

    static final String BASE_PACKAGE = "com.example.lending";
    private static final Path REPORT = Path.of("build", "arch", "lending.json");

    @ArchTest
    void hexagonal_rules_hold(JavaClasses classes) throws IOException {
        if (classes.stream().noneMatch(c -> c.getPackageName().startsWith(BASE_PACKAGE + ".domain"))) {
            throw new AssertionError("no classes imported under " + BASE_PACKAGE + ".domain — wrong base package?");
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        StringBuilder failures = new StringBuilder();
        for (NamedRule namedRule : HexagonalRules.forBasePackage(BASE_PACKAGE)) {
            EvaluationResult result = namedRule.rule().evaluate(classes);
            int violations = result.getFailureReport().getDetails().size();
            counts.put(namedRule.id(), violations);
            System.out.printf("[arch] %s %-45s %s (%d)%n", namedRule.id(), namedRule.name(),
                    violations == 0 ? "PASS" : "FAIL", violations);
            if (result.hasViolation()) {
                failures.append(namedRule.id()).append(' ').append(namedRule.name()).append('\n')
                        .append(result.getFailureReport()).append('\n');
            }
        }

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, counts.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\": " + e.getValue())
                .collect(Collectors.joining(", ", "{", "}\n")));
        System.out.println("[arch] wrote " + REPORT.toAbsolutePath());

        if (!failures.isEmpty()) {
            throw new AssertionError("Hexagonal rules violated in " + BASE_PACKAGE + ":\n" + failures);
        }
    }
}
