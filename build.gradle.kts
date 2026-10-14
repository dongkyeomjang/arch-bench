import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.util.concurrent.atomic.AtomicLong
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("org.springframework.boot") version "3.5.3" apply false
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    dependencies {
        "implementation"(platform(SpringBootPlugin.BOM_COORDINATES))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("com.tngtech.archunit:archunit-junit5:1.3.2")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-parameters")
    }

    // 일반 test는 기능 테스트만. 아키텍처 측정은 archTest로 분리한다.
    tasks.named<Test>("test") {
        useJUnitPlatform {
            excludeTags("arch", "investigation")
        }
    }

    val sourceSets = the<SourceSetContainer>()

    tasks.register<Test>("archTest") {
        description = "Runs @Tag(\"arch\") tests (hexagonal ArchUnit rules)."
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
        }
        // 측정기이므로 매번 다시 실행한다 (lending.json 갱신).
        outputs.upToDateWhen { false }
        // 테스트가 발견되지 않아 실행되지 않으면 이전 측정값이 남지 않도록 먼저 지운다.
        val archOutput = layout.buildDirectory.dir("arch")
        doFirst { delete(archOutput) }

        // 가드: 태그 필터·엔진 설정 오류로 arch 테스트가 0개 실행되면 "통과"가 아니라 실패로 처리한다.
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
}
