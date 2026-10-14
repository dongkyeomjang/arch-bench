plugins {
    java
    id("org.springframework.boot")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// violation-fixtures가 같은 규칙 객체(HexagonalRules)와 추출기(ViolationExtractor)를 쓰도록 두 클래스만 jar로 노출한다.
val archRulesJar by tasks.registering(Jar::class) {
    archiveClassifier = "arch-rules"
    from(sourceSets.test.get().output) {
        include("com/example/lending/architecture/HexagonalRules*")
        include("com/example/lending/architecture/ViolationExtractor*")
    }
}

val archRules by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts.add(archRules.name, archRulesJar)
