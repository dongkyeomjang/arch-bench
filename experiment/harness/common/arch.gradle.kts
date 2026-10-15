import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.util.concurrent.atomic.AtomicLong

// ArchUnit 구조 검사. ./gradlew archTest 로 실행한다.

dependencies {
    "testImplementation"("com.tngtech.archunit:archunit-junit5:1.3.2")
}

// ./gradlew test 는 기능 테스트만 실행한다. 구조 검사는 archTest 로 분리한다.
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("arch")
    }
}

val sourceSets = the<SourceSetContainer>()

tasks.register<Test>("archTest") {
    description = "Runs @Tag(\"arch\") tests (ArchUnit rules)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("arch")
    }
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = true
        // -q 실행에서도 실패한 규칙 이름과 위반 상세가 출력되게 한다.
        quiet {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
    outputs.upToDateWhen { false }
    val archOutput = layout.buildDirectory.dir("arch")
    doFirst { delete(archOutput) }

    val executedTests = AtomicLong()
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun beforeTest(testDescriptor: TestDescriptor) {}
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent == null) {
                executedTests.set(result.testCount - result.skippedTestCount)
            }
        }
    })
    doLast {
        if (executedTests.get() == 0L) {
            throw GradleException("$path executed 0 @Tag(\"arch\") tests — check @ArchTag/@Tag and the test engine")
        }
        logger.lifecycle("$path executed ${executedTests.get()} arch tests")
    }
}
