/**
 * Append-only record of who decided or changed what, and when. Other modules write to it through
 * {@link com.oneroof.audit.AuditLog}; nothing here is ever updated or deleted.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Audit log")
package com.oneroof.audit;
