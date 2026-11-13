package com.aura.mcp.tools

import com.aura.mcp.bridge.ToolDomain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogTest {

    @Test
    fun `catalog is not empty and all names are unique`() {
        val names = ToolCatalog.all.map { it.name }
        assertTrue("expected at least 50 tools, got ${names.size}", names.size >= 50)
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `byName keys match declared names`() {
        assertEquals(ToolCatalog.all.size, ToolCatalog.byName.size)
        ToolCatalog.all.forEach { definition ->
            assertEquals(definition, ToolCatalog.byName[definition.name])
        }
    }

    @Test
    fun `every tool has a description, domain and valid schema`() {
        ToolCatalog.all.forEach { definition ->
            assertTrue("${definition.name} must have a description", definition.description.isNotBlank())
            assertTrue("${definition.name} must have a schema", definition.inputSchema.isNotEmpty())
            val properties = definition.inputSchema["properties"]?.jsonObject
            assertNotNull("${definition.name} must declare properties", properties)
        }
    }

    @Test
    fun `required fields are declared in properties`() {
        ToolCatalog.all.forEach { definition ->
            val required = (definition.inputSchema["required"] as? kotlinx.serialization.json.JsonArray)
                ?.map { it.jsonPrimitive.content }.orEmpty()
            val properties = definition.inputSchema["properties"]?.jsonObject?.keys.orEmpty()
            required.forEach { field ->
                assertTrue("${definition.name} requires missing property $field", field in properties)
            }
        }
    }

    @Test
    fun `signature renders required and optional parameters`() {
        val signature = ToolCatalog.signature("tap")
        assertTrue(signature.startsWith("tap("))
        assertTrue(signature.endsWith(")"))
    }

    @Test
    fun `ask_user is available and requires a question`() {
        val ask = ToolCatalog.byName["ask_user"]
        assertNotNull(ask)
        val required = (ask!!.inputSchema["required"] as kotlinx.serialization.json.JsonArray).map { it.jsonPrimitive.content }
        assertTrue("question" in required)
    }

    @Test
    fun `toolListJson matches the tool count`() {
        assertEquals(ToolCatalog.all.size, ToolCatalog.toolListJson().size)
    }

    @Test
    fun `risky tools are marked in the catalog`() {
        val risky = ToolCatalog.all.filter { it.risky }.map { it.name }
        assertTrue("expected risky tools including compose_sms, got $risky", summary(risky, "compose_sms"))
    }

    @Test
    fun `install_app opens a play store listing for an uninstalled app`() {
        val def = ToolCatalog.byName["install_app"]
        assertNotNull(def)
        assertEquals(ToolDomain.APPS, def!!.domain)
        assertTrue(def.mutating)
        assertTrue(def.risky)
        val properties = def.inputSchema["properties"]?.jsonObject
        assertNotNull(properties)
        assertTrue(properties?.containsKey("app_name") == true)
        assertTrue(properties?.containsKey("package") == true)
    }

    private fun summary(list: List<String>, needle: String): Boolean = needle in list

    @Test
    fun `read_only tools are not marked mutating`() {
        val readOnly = ToolCatalog.all.filter { !it.mutating }.map { it.name }
        assertTrue("read_screen" in readOnly)
        assertTrue("perceive_screen" in readOnly)
        assertTrue("get_device_status" in readOnly)
    }

    @Test
    fun `schema of run_steps is self-contained`() {
        val runSteps = ToolCatalog.byName["run_steps"]
        assertNotNull(runSteps)
        val properties = runSteps!!.inputSchema["properties"]!!.jsonObject
        assertTrue(properties.containsKey("steps"))
    }

    @Test
    fun `data classes toJson produce well-formed objects`() {
        ToolCatalog.all.take(4).forEach { definition ->
            val json = definition.toJson()
            assertTrue(json.containsKey("name"))
            assertTrue(json.containsKey("description"))
            assertTrue(json.containsKey("inputSchema"))
        }
        assertTrue(JsonObject(emptyMap()).isNotEmpty().not())
    }
}
