package com.example.lending.architecture;

import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.List;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noCodeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 헥사고날 아키텍처 규칙 R1~R6. 양성(lending)·음성(violation-fixtures) 검증이 같은 규칙 객체를 쓰도록
 * 기준 패키지를 인자로 받는다. 번호(id)는 실험 결과 JSON의 키다.
 *
 * <p>모든 규칙은 allowEmptyShould(true): 축소 픽스처처럼 특정 레이어가 없는 저장소에서
 * "검사 대상 없음"이 위반 1건으로 집계되지 않게 한다. 기준 패키지 오타 방어는 각 테스트가 import 결과를 직접 확인한다.
 */
public final class HexagonalRules {

    public record NamedRule(String id, String name, ArchRule rule) {
    }

    private static final String ENTITY = "jakarta.persistence.Entity";
    private static final String COMPONENT = "org.springframework.stereotype.Component";
    private static final String SERVICE = "org.springframework.stereotype.Service";
    private static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";

    private HexagonalRules() {
    }

    public static List<NamedRule> forBasePackage(String basePackage) {
        String domain = basePackage + ".domain..";
        String application = basePackage + ".application..";
        String adapter = basePackage + ".adapter..";
        String adapterIn = basePackage + ".adapter.in..";
        String adapterOut = basePackage + ".adapter.out..";
        String port = basePackage + ".application.port..";
        String portOut = basePackage + ".application.port.out..";

        String r1Reason = "domain은 ..application.., ..adapter.., org.springframework.., jakarta.persistence..을 의존하지 않는다";
        ArchRule r1 = noClasses().that().resideInAPackage(domain)
                .should().dependOnClassesThat().resideInAnyPackage(
                        application, adapter, "org.springframework..", "jakarta.persistence..")
                .because(r1Reason)
                .allowEmptyShould(true);

        ArchRule r2 = noClasses().that().resideInAPackage(application)
                .should().dependOnClassesThat().resideInAPackage(adapter)
                .because("application은 ..adapter..를 의존하지 않는다")
                .allowEmptyShould(true);

        String r3Reason = "adapter.in과 adapter.out은 서로 의존하지 않는다";
        ArchRule r3 = CompositeArchRule
                .of(noClasses().that().resideInAPackage(adapterIn)
                        .should().dependOnClassesThat().resideInAPackage(adapterOut)
                        .because(r3Reason)
                        .allowEmptyShould(true))
                .and(noClasses().that().resideInAPackage(adapterOut)
                        .should().dependOnClassesThat().resideInAPackage(adapterIn)
                        .because(r3Reason)
                        .allowEmptyShould(true));

        // 패턴 A: domain / application / adapter 3개 슬라이스. 패턴 비교는 REPORT.md 단계 4 참조
        ArchRule r4 = slices().matching(basePackage + ".(*)..")
                .should().beFreeOfCycles()
                .because("레이어 간 순환 의존이 없다")
                .allowEmptyShould(true);

        // @Entity/@Component/@Service는 TYPE 대상이지만 @JsonProperty는 TYPE에 붙을 수 없으므로
        // (FIELD/METHOD/PARAMETER/ANNOTATION_TYPE) 클래스 수준 검사만으로는 절대 잡히지 않는다. 멤버·파라미터까지 본다.
        String r5Reason = "domain 클래스에 @Entity, @Component, @Service, @JsonProperty가 없다";
        ArchRule r5 = CompositeArchRule
                .of(noClasses().that().resideInAPackage(domain)
                        .should().beAnnotatedWith(ENTITY)
                        .orShould().beAnnotatedWith(COMPONENT)
                        .orShould().beAnnotatedWith(SERVICE)
                        .orShould().beAnnotatedWith(JSON_PROPERTY)
                        .because(r5Reason)
                        .allowEmptyShould(true))
                .and(noFields().that().areDeclaredInClassesThat().resideInAPackage(domain)
                        .should().beAnnotatedWith(JSON_PROPERTY)
                        .because(r5Reason)
                        .allowEmptyShould(true))
                .and(noCodeUnits().that().areDeclaredInClassesThat().resideInAPackage(domain)
                        .should().beAnnotatedWith(JSON_PROPERTY)
                        .because(r5Reason)
                        .allowEmptyShould(true))
                .and(codeUnits().that().areDeclaredInClassesThat().resideInAPackage(domain)
                        .should(notHaveParametersAnnotatedWith(JSON_PROPERTY))
                        .because(r5Reason)
                        .allowEmptyShould(true));

        // record는 포트 계약에 딸린 입력 데이터(예: LoanBookUseCase.LoanBookCommand)라서 interface 요구에서 제외한다.
        // record는 상속·구현이 불가능해 포트 역할을 할 수 없으므로 이 예외로 추상 클래스 포트가 빠져나가지 않는다.
        String r6Reason = "application.port 아래 타입은 interface이며, port.out 인터페이스의 구현체는 adapter.out에만 있다";
        ArchRule r6 = CompositeArchRule
                .of(classes().that().resideInAPackage(port)
                        .and().areNotAssignableTo(Record.class)
                        .should().beInterfaces()
                        .because(r6Reason)
                        .allowEmptyShould(true))
                .and(classes().that().areAssignableTo(resideInAPackage(portOut))
                        .and().resideOutsideOfPackage(portOut)
                        .should().resideInAPackage(adapterOut)
                        .because(r6Reason)
                        .allowEmptyShould(true));

        return List.of(
                new NamedRule("R1", "domain-no-outward-deps", r1),
                new NamedRule("R2", "application-no-adapter-deps", r2),
                new NamedRule("R3", "adapters-independent", r3),
                new NamedRule("R4", "no-layer-cycles", r4),
                new NamedRule("R5", "domain-no-framework-annotations", r5),
                new NamedRule("R6", "ports-are-interfaces-implemented-in-adapters", r6));
    }

    private static ArchCondition<JavaCodeUnit> notHaveParametersAnnotatedWith(String annotationName) {
        return new ArchCondition<>("not have parameters annotated with @" + annotationName) {
            @Override
            public void check(JavaCodeUnit codeUnit, ConditionEvents events) {
                for (JavaParameter parameter : codeUnit.getParameters()) {
                    if (parameter.isAnnotatedWith(annotationName)) {
                        events.add(SimpleConditionEvent.violated(codeUnit, String.format(
                                "Parameter %d of %s is annotated with @%s",
                                parameter.getIndex(), codeUnit.getFullName(), annotationName)));
                    }
                }
            }
        };
    }
}
