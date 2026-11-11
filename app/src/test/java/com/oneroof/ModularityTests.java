package com.oneroof;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(OneroofApplication.class);

	@Test
	void modulesRespectTheirBoundaries() {
		this.modules.verify();
	}

	@Test
	void writesModuleDocumentation() {
		new Documenter(this.modules).writeDocumentation();
	}
}
