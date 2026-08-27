plugins {
	java
	checkstyle
	id("org.springframework.boot") version "4.0.7"
	id("io.spring.dependency-management") version "1.1.7"
	id("jacoco")
	id("com.diffplug.spotless") version "8.10.0"
	id("org.owasp.dependencycheck") version "12.1.3"
}

group = "com.mel.fernbank"
version = "0.0.1-SNAPSHOT"
description = "fernbank ledger service"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
	implementation("org.mapstruct:mapstruct:1.6.3")
	annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
	implementation("com.bucket4j:bucket4j-core:8.10.1")
	implementation("commons-codec:commons-codec:1.22.0")
	// Statement PDF export: low-level content-stream API, no HTML/CSS templating engine.
	implementation("org.apache.pdfbox:pdfbox:3.0.3")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("org.bouncycastle:bcprov-jdk18on:1.80")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)
}

val jacocoCoverageExclusions = listOf(
	"**/api/dto/**",
	"**/api/mapper/*MapperImpl.class",
	"**/auth/dto/**",
	"**/config/**",
	"**/repository/**",
	"**/*Controller.class",
	"**/*Application.class",
	"**/security/JwtConfig.class",
	"**/security/SecurityConfig.class",
	"**/security/PasswordEncoderConfig.class",
	// Ops bootstrap glue, not domain logic - it only orchestrates calls into already
	// -tested services (AuthenticationService, OpenAccountService, ...) and is
	// verified functionally against the real docker-compose stack (fresh seed +
	// restart-idempotent), same reasoning as config/controller exclusions above.
	"**/demo/**",
)

fun JacocoReportBase.excludeNonServiceCode() {
	classDirectories.setFrom(files(classDirectories.files.map { dir ->
		fileTree(dir) { exclude(jacocoCoverageExclusions) }
	}))
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
		html.required = true
		// cicirello/jacoco-badge-generator (backend-ci.yml) reads this CSV to compute the
		// README coverage badge - without it the badge-generation step fails with
		// "No JaCoCo csv reports found" even though the actual test run passed.
		csv.required = true
	}
	excludeNonServiceCode()
}

tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.jacocoTestReport)
	excludeNonServiceCode()
	violationRules {
		rule {
			element = "BUNDLE"
			limit {
				counter = "LINE"
				minimum = "0.80".toBigDecimal()
			}
			limit {
				counter = "BRANCH"
				minimum = "0.70".toBigDecimal()
			}
		}
	}
}

tasks.check {
	dependsOn(tasks.jacocoTestCoverageVerification)
	dependsOn(tasks.named("dependencyCheckAnalyze"))
}

jacoco {
	toolVersion = "0.8.12"
}

checkstyle {
	toolVersion = "10.21.4"
	configFile = file("config/checkstyle/checkstyle.xml")
	isIgnoreFailures = false
	sourceSets = listOf(project.sourceSets.main.get())
}

spotless {
	java {
		removeUnusedImports()
		importOrder()
		target("src/**/*.java")
	}
	kotlinGradle {
		target("*.gradle.kts")
	}
}

dependencyCheck {
	failBuildOnCVSS = 7.0f
	formats = listOf("HTML", "JSON")
	suppressionFile = "config/dependency-check/suppressions.xml"
	nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""
	// Scan only what actually ships at runtime - not build-tool-only classpaths like
	// checkstyle/jacocoAgent/annotationProcessor, which pull in their own transitive
	// dependencies (e.g. Checkstyle's own commons-beanutils/httpclient5) that never run
	// as part of this application and would otherwise show up as false "app" findings.
	// productionRuntimeClasspath (Spring Boot's own "what ships" configuration), not
	// plain runtimeClasspath - the plugin's own test-configuration heuristic
	// misclassifies runtimeClasspath here (its extendsFrom chain reaches
	// testAndDevelopmentOnly) and silently skips it, scanning nothing.
	scanConfigurations = listOf("productionRuntimeClasspath")
	// Sonatype OSS Index is a supplementary check on top of the NVD data above; it now
	// returns 401 Unauthorized for anonymous requests (requires its own separate
	// account/token), which isn't worth requiring for this project on top of NVD's own
	// key. NVD is the CVE database Phase 5 actually asked for.
	analyzers.ossIndex.enabled = false
}
