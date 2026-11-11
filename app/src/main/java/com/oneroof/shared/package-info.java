/**
 * Shared kernel: value types and the business clock that every module may use.
 * It holds no business rules and depends on no other module.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Shared kernel",
		type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.oneroof.shared;
