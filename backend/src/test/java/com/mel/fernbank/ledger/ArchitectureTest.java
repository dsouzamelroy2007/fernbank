package com.mel.fernbank.ledger;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.mel.fernbank.ledger", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule domain_has_no_spring_dependency = noClasses()
			.that()
			.resideInAPackage("..domain..")
			.should()
			.dependOnClassesThat()
			.resideInAnyPackage("org.springframework..")
			.because("domain must stay Spring-free by design (see MfaSecretConverter's pure-JDK crypto, Phase 2)");

	@ArchTest
	static final ArchRule controllers_do_not_touch_repositories = noClasses()
			.that()
			.haveSimpleNameEndingWith("Controller")
			.should()
			.dependOnClassesThat()
			.resideInAPackage("..repository..")
			.because("controllers must go through a service/resolver, never call a repository directly");
}
