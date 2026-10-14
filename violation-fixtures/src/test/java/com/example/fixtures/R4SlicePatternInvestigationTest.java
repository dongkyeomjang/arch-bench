package com.example.fixtures;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 단계 4 조사용(측정기 아님). R4 슬라이스 패턴 A/B/C를 lending과 v1~v6에 적용해 결과를 출력한다.
 * 실행: ./gradlew :violation-fixtures:r4Investigation
 */
@Tag("investigation")
class R4SlicePatternInvestigationTest {

    private static final List<String> TARGETS = List.of(
            "com.example.lending",
            "com.example.fixtures.v1", "com.example.fixtures.v2", "com.example.fixtures.v3",
            "com.example.fixtures.v4", "com.example.fixtures.v5", "com.example.fixtures.v6");

    @Test
    void compare_slice_patterns() {
        List<Pattern> patterns = List.of(
                new Pattern("A  base.(*)..", base -> slices().matching(base + ".(*)..")
                        .should().beFreeOfCycles().allowEmptyShould(true)),
                new Pattern("B  base.(**)", base -> slices().matching(base + ".(**)")
                        .should().beFreeOfCycles().allowEmptyShould(true)),
                new Pattern("C  base.adapter.(*).. + base.application.(*)..", base -> CompositeArchRule
                        .of(slices().matching(base + ".adapter.(*)..").should().beFreeOfCycles().allowEmptyShould(true))
                        .and(slices().matching(base + ".application.(*)..").should().beFreeOfCycles().allowEmptyShould(true))));

        for (String target : TARGETS) {
            JavaClasses classes = new ClassFileImporter()
                    .withImportOption(new DoNotIncludeTests())
                    .importPackages(target);
            for (Pattern pattern : patterns) {
                EvaluationResult result = pattern.rule().apply(target).evaluate(classes);
                List<String> details = result.getFailureReport().getDetails();
                System.out.printf("[r4] %-24s | %-48s | %s%n", target, pattern.name(),
                        details.isEmpty() ? "pass" : "FAIL(" + details.size() + ")");
                details.forEach(d -> System.out.println("       " + d.lines().findFirst().orElse("")
                        + " " + d.lines().skip(1).limit(2).map(String::strip).reduce("", (a, b) -> a + " " + b)));
            }
        }
    }

    private record Pattern(String name, Function<String, ArchRule> rule) {
    }
}
