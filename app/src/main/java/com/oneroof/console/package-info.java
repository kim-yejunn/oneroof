/**
 * The read side and the HTTP surface. The contract lives in {@code api}: interfaces carrying the
 * OpenAPI documentation and the request and response records. {@code web} implements it. Lists are
 * read from database views; evidence that needs the domain rules is read through other modules'
 * public services. Commands are passed on to the owning module unchanged; no business rule lives here.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Console")
package com.oneroof.console;
