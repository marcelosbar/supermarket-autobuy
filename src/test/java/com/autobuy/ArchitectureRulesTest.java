package com.autobuy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.autobuy", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

	@ArchTest
	static final ArchRule serviceClassesMustNotDependOnWebPackage = noClasses().that()
			.resideInAPackage("com.autobuy.service..").should().dependOnClassesThat()
			.resideInAPackage("com.autobuy.web..");

	@ArchTest
	static final ArchRule repositoryInterfacesMustOnlyDependOnModel = noClasses().that()
			.resideInAPackage("com.autobuy.repository..").should().dependOnClassesThat()
			.resideInAnyPackage("com.autobuy.web..", "com.autobuy.service..", "com.autobuy.provider..",
					"com.autobuy.driver..", "com.autobuy.config..", "com.autobuy.exception..");

	@ArchTest
	static final ArchRule modelClassesMustNotDependOnSiblingPackages = noClasses().that()
			.resideInAPackage("com.autobuy.model..").should().dependOnClassesThat().resideInAnyPackage(
					"com.autobuy.web..", "com.autobuy.service..", "com.autobuy.repository..", "com.autobuy.provider..",
					"com.autobuy.driver..", "com.autobuy.config..", "com.autobuy.exception..");

	@ArchTest
	static final ArchRule webControllersMustNotAccessRepositoryDirectly = noClasses().that()
			.areAnnotatedWith(RestController.class).or().resideInAPackage("com.autobuy.web..").should()
			.dependOnClassesThat().resideInAPackage("com.autobuy.repository..");

	@ArchTest
	static final ArchRule exceptionClassesMustExtendAutoBuyException = classes().that()
			.resideInAPackage("com.autobuy.exception..").and().doNotHaveSimpleName("package-info").should()
			.beAssignableTo(com.autobuy.exception.AutoBuyException.class);

	@ArchTest
	static final ArchRule providerAndDriverClassesMustBePackagePrivateOrImplementPublicInterface = classes().that()
			.resideInAnyPackage("com.autobuy.provider..", "com.autobuy.driver..").and().arePublic().and()
			.areNotInterfaces()
			.should(new ArchCondition<JavaClass>("be package-private or implement a public interface") {
				@Override
				public void check(JavaClass item, ConditionEvents events) {
					boolean implementsPublicInterface = item.getRawInterfaces().stream()
							.anyMatch(i -> i.getModifiers().contains(JavaModifier.PUBLIC));
					boolean isAbstract = item.getModifiers().contains(JavaModifier.ABSTRACT);
					if (!implementsPublicInterface && !isAbstract) {
						String message = String.format("Class %s is public but does not implement a public interface",
								item.getName());
						events.add(SimpleConditionEvent.violated(item, message));
					}
				}
			});

	@ArchTest
	static final ArchRule restControllerMethodsMustNotReturnMap = methods().that().areDeclaredInClassesThat()
			.areAnnotatedWith(RestController.class)
			.should(new ArchCondition<JavaMethod>("not return Map<String, Object>") {
				@Override
				public void check(JavaMethod method, ConditionEvents events) {
					JavaClass returnType = method.getRawReturnType();
					if (returnType.isAssignableTo(Map.class)) {
						String message = String.format("Controller method %s returns a Map instead of a typed DTO",
								method.getFullName());
						events.add(SimpleConditionEvent.violated(method, message));
					}
				}
			});
}
