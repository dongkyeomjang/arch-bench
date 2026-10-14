package com.example.lending.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.library.cycle_detection.Cycle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 규칙 평가 결과에서 위반의 원인 클래스(FQN)를 뽑고, 측정 JSON 구조를 만든다.
 * 실패 리포트 문자열을 파싱하지 않고 ArchUnit 위반 객체의 타입으로 판단한다.
 *
 * <ul>
 *   <li>{@link Dependency}, {@link JavaAccess} (R1~R3): 의존의 출발 클래스</li>
 *   <li>{@link Cycle} (R4): 순환을 이루는 의존들의 출발·도착 클래스 전부</li>
 *   <li>{@link JavaMember} (R5 필드·메서드·생성자·파라미터): 멤버의 소유 클래스</li>
 *   <li>{@link JavaClass} (R5 클래스, R6, R7): 그 클래스</li>
 * </ul>
 * 위 타입이 아닌 위반 객체가 오면 조용히 버리지 않고 예외를 던진다(집계 누락 방지).
 */
public final class ViolationExtractor {

    private ViolationExtractor() {
    }

    public static SortedSet<String> violatingClasses(EvaluationResult result) {
        SortedSet<String> classes = new TreeSet<>();
        List<String> unhandled = new ArrayList<>();
        // T=Object로 받아 원본 위반 객체를 변환 없이 받는다. 타입별 핸들러를 따로 등록하면 모르는 타입이 조용히 누락된다.
        result.handleViolations((Collection<Object> violatingObjects, String message) -> {
            for (Object violatingObject : violatingObjects) {
                if (violatingObject instanceof Dependency dependency) {
                    classes.add(dependency.getOriginClass().getName());
                } else if (violatingObject instanceof JavaAccess<?> access) {
                    classes.add(access.getOriginOwner().getName());
                } else if (violatingObject instanceof Cycle<?> cycle) {
                    for (Dependency dependency : cycle.convertTo(Dependency.class)) {
                        classes.add(dependency.getOriginClass().getName());
                        classes.add(dependency.getTargetClass().getName());
                    }
                } else if (violatingObject instanceof JavaMember member) {
                    classes.add(member.getOwner().getName());
                } else if (violatingObject instanceof JavaClass javaClass) {
                    classes.add(javaClass.getName());
                } else {
                    unhandled.add(violatingObject.getClass().getName() + ": " + message);
                }
            }
        });
        if (!unhandled.isEmpty()) {
            throw new IllegalStateException("unhandled violation object types:\n" + String.join("\n", unhandled));
        }
        if (result.hasViolation() && classes.isEmpty()) {
            throw new IllegalStateException("rule has violations but no cause class was extracted:\n"
                    + result.getFailureReport());
        }
        return classes;
    }

    /**
     * 주 지표(violating_classes) 합집합에 들어가는 규칙. R4·R5는 by_rule에만 남긴다.
     * R4: 순환의 정상 방향 쪽 클래스(예: v4의 LoanPolicy)까지 원인으로 잡아 과대 집계한다. 패턴 A에서 R4는 R1 ∪ R2에 포함된다.
     * R5: 4개 어노테이션이 모두 R1 블랙리스트 패키지라서 원인 클래스가 항상 R1의 부분집합이다(R5 ⊂ R1).
     */
    public static final List<String> PRIMARY_RULES = List.of("R1", "R2", "R3", "R6", "R7");

    /**
     * 측정 JSON 구조. {@code resultsByRuleId}는 실행된 규칙만 규칙 순서대로 담는다
     * (실행되지 않은 규칙을 0으로 기록하지 않기 위해). tests_executed = 실행된 규칙 테스트 수.
     */
    public static Map<String, Object> report(Map<String, EvaluationResult> resultsByRuleId) {
        SortedSet<String> primaryClasses = new TreeSet<>();
        Map<String, Object> byRule = new LinkedHashMap<>();
        resultsByRuleId.forEach((ruleId, result) -> {
            SortedSet<String> classes = violatingClasses(result);
            if (PRIMARY_RULES.contains(ruleId)) {
                primaryClasses.addAll(classes);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("count", result.getFailureReport().getDetails().size());
            entry.put("classes", List.copyOf(classes));
            byRule.put(ruleId, entry);
        });

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("tests_executed", resultsByRuleId.size());
        report.put("primary_rules", PRIMARY_RULES);
        report.put("violating_classes", primaryClasses.size());
        report.put("violating_classes_list", List.copyOf(primaryClasses));
        report.put("by_rule", byRule);
        return report;
    }
}
