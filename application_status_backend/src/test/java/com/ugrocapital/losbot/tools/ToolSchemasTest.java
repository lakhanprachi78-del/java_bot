package com.ugrocapital.losbot.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ToolSchemasTest {
    @Test
    void exposesSevenDataToolsAndOneScopeToolWithoutIdentityArguments() {
        assertEquals(8, ToolSchemas.SCHEMAS.size());
        assertEquals("pre-login", ToolSchemas.STATUS_CODES.get(0));
        assertEquals("sent to lms", ToolSchemas.STATUS_CODES.get(8));
        assertFalse(ToolSchemas.SCHEMAS.toString().contains("username"));
        assertFalse(ToolSchemas.SCHEMAS.toString().contains("auth"));
    }
}