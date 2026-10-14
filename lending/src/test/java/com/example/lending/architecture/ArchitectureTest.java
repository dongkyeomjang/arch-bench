package com.example.lending.architecture;

import com.example.lending.architecture.HexagonalRules.NamedRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 양성 검증: 올바른 참조 구현(lending)에서 R1~R7이 모두 통과해야 한다. 규칙당 테스트 1개.
 * 각 테스트는 실패 여부와 관계없이, 지금까지 실행된 규칙 결과로 build/arch/lending.json을 다시 쓴 뒤 assert한다.
 * 따라서 tests_executed는 실제로 실행된 규칙 테스트 수이며, 파이프라인은 이 값이 규칙 수(7)와 같을 때만 측정값으로 쓴다.
 *
 * <p>@Tag만으로는 ArchUnit JUnit5 엔진 테스트가 태그 필터되지 않으므로 @ArchTag를 반드시 함께 단다.
 */
@Tag("arch")
@ArchTag("arch")
@AnalyzeClasses(packages = ArchitectureTest.BASE_PACKAGE, importOptions = DoNotIncludeTests.class)
class ArchitectureTest {

    static final String BASE_PACKAGE = "com.example.lending";
    private static final Path REPORT = Path.of("build", "arch", "lending.json");

    private static final List<NamedRule> RULES = HexagonalRules.forBasePackage(BASE_PACKAGE);
    private static final Set<String> RULE_IDS_COVERED_BY_THIS_CLASS = Set.of("R1", "R2", "R3", "R4", "R5", "R6", "R7");
    private static final Map<String, EvaluationResult> RESULTS = new HashMap<>();

    @ArchTest
    void r1_domain_no_outward_deps(JavaClasses classes) throws IOException {
        evaluate("R1", classes);
    }

    @ArchTest
    void r2_application_no_adapter_deps(JavaClasses classes) throws IOException {
        evaluate("R2", classes);
    }

    @ArchTest
    void r3_adapters_independent(JavaClasses classes) throws IOException {
        evaluate("R3", classes);
    }

    @ArchTest
    void r4_no_layer_cycles(JavaClasses classes) throws IOException {
        evaluate("R4", classes);
    }

    @ArchTest
    void r5_domain_no_framework_annotations(JavaClasses classes) throws IOException {
        evaluate("R5", classes);
    }

    @ArchTest
    void r6_ports_are_interfaces_implemented_in_adapters(JavaClasses classes) throws IOException {
        evaluate("R6", classes);
    }

    @ArchTest
    void r7_only_three_top_level_packages(JavaClasses classes) throws IOException {
        evaluate("R7", classes);
    }

    private static synchronized void evaluate(String ruleId, JavaClasses classes) throws IOException {
        Set<String> ruleIds = RULES.stream().map(NamedRule::id).collect(Collectors.toSet());
        if (!ruleIds.equals(RULE_IDS_COVERED_BY_THIS_CLASS)) {
            throw new AssertionError("HexagonalRules defines " + ruleIds + " but ArchitectureTest has test methods for "
                    + RULE_IDS_COVERED_BY_THIS_CLASS + " — add/remove a test method per rule");
        }
        if (classes.stream().noneMatch(c -> c.getPackageName().startsWith(BASE_PACKAGE + ".domain"))) {
            throw new AssertionError("no classes imported under " + BASE_PACKAGE + ".domain — wrong base package?");
        }

        NamedRule namedRule = RULES.stream().filter(r -> r.id().equals(ruleId)).findFirst().orElseThrow();
        EvaluationResult result = namedRule.rule().evaluate(classes);
        RESULTS.put(ruleId, result);
        writeReport();

        int violations = result.getFailureReport().getDetails().size();
        System.out.printf("[arch] %s %-45s %s (%d) %s%n", ruleId, namedRule.name(),
                violations == 0 ? "PASS" : "FAIL", violations, ViolationExtractor.violatingClasses(result));
        if (result.hasViolation()) {
            throw new AssertionError(ruleId + " " + namedRule.name() + " violated in " + BASE_PACKAGE + ":\n"
                    + result.getFailureReport());
        }
    }

    private static void writeReport() throws IOException {
        Map<String, EvaluationResult> executedInRuleOrder = new LinkedHashMap<>();
        RULES.stream().filter(r -> RESULTS.containsKey(r.id()))
                .forEach(r -> executedInRuleOrder.put(r.id(), RESULTS.get(r.id())));

        Files.createDirectories(REPORT.getParent());
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(REPORT.toFile(), ViolationExtractor.report(executedInRuleOrder));
    }
}
