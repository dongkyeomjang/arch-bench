plugins {
    java
}

dependencies {
    // 픽스처가 실제 Spring/JPA/Jackson 어노테이션과 타입을 쓰므로 컴파일 의존성이 필요하다.
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // v8: 도메인의 jakarta.validation 어노테이션(R1 위반)을 컴파일하기 위함
    implementation("jakarta.validation:jakarta.validation-api")

    testImplementation(project(path = ":lending", configuration = "archRules"))
    // R4 슬라이스 패턴 조사(R4SlicePatternInvestigationTest)에서 lending 클래스를 함께 import하기 위함
    testImplementation(project(":lending"))
}

// 단계 4: R4 슬라이스 패턴 조사. archTest와 분리해 수동 실행한다.
tasks.register<Test>("r4Investigation") {
    description = "Prints R4 slice pattern comparison (lending + fixtures)."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("investigation")
    }
    testLogging {
        showStandardStreams = true
    }
    outputs.upToDateWhen { false }
}
