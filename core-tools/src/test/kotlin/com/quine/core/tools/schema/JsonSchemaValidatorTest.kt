package com.quine.core.tools.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonSchemaValidatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val fsReadSchema = schema(
        """
        {
          "type": "object",
          "properties": {
            "path": { "type": "string" },
            "offset": { "type": "integer" },
            "limit": { "type": "integer" }
          },
          "required": ["path"],
          "additionalProperties": false
        }
        """,
    )

    @Test
    fun `合法参数通过`() {
        val result = JsonSchemaValidator.validate(fsReadSchema, args("""{"path":"notes/todo.md"}"""))
        assertEquals(SchemaValidation.Valid, result)
    }

    @Test
    fun `缺少必填字段`() {
        val errors = errors(fsReadSchema, args("{}"))
        assertEquals(1, errors.size)
        assertEquals("path", errors.first().path)
    }

    @Test
    fun `类型不对`() {
        val errors = errors(fsReadSchema, args("""{"path":123}"""))
        assertEquals("path", errors.first().path)
        assertTrue(errors.first().message.contains("类型"))
    }

    @Test
    fun `不认识的字段被拒`() {
        val errors = errors(fsReadSchema, args("""{"path":"a.txt","recursive":true}"""))
        assertEquals("recursive", errors.first().path)
    }

    @Test
    fun `integer 同时接受 1 与 1点0`() {
        assertEquals(SchemaValidation.Valid, JsonSchemaValidator.validate(fsReadSchema, args("""{"path":"a","offset":1}""")))
        assertEquals(SchemaValidation.Valid, JsonSchemaValidator.validate(fsReadSchema, args("""{"path":"a","offset":1.0}""")))
        assertTrue(JsonSchemaValidator.validate(fsReadSchema, args("""{"path":"a","offset":1.5}""")) is SchemaValidation.Invalid)
    }

    @Test
    fun `enum 之外的值被拒`() {
        val enumSchema = schema("""{"type":"string","enum":["read","write"]}""")
        assertEquals(SchemaValidation.Valid, JsonSchemaValidator.validate(enumSchema, args("\"read\"")))
        assertTrue(JsonSchemaValidator.validate(enumSchema, args("\"delete\"")) is SchemaValidation.Invalid)
    }

    @Test
    fun `数组逐项校验`() {
        val arraySchema = schema(
            """{"type":"array","items":{"type":"object","properties":{"n":{"type":"string"}},"required":["n"]}}""",
        )
        assertEquals(SchemaValidation.Valid, JsonSchemaValidator.validate(arraySchema, args("""[{"n":"a"}]""")))
        val errors = errors(arraySchema, args("""[{"n":"a"},{"x":1}]"""))
        assertEquals(1, errors.size)
        assertTrue(errors.first().path.startsWith("[1]"))
    }

    @Test
    fun `允许的类型列表`() {
        val nullable = schema("""{"type":["string","null"]}""")
        assertEquals(SchemaValidation.Valid, JsonSchemaValidator.validate(nullable, args("null")))
        assertEquals(SchemaValidation.Valid, JsonSchemaValidator.validate(nullable, args("\"x\"")))
    }

    private fun errors(schema: JsonObject, value: kotlinx.serialization.json.JsonElement): List<FieldError> {
        val result = JsonSchemaValidator.validate(schema, value)
        assertTrue("期望校验失败，实际通过", result is SchemaValidation.Invalid)
        return (result as SchemaValidation.Invalid).errors
    }

    private fun schema(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    private fun args(text: String): kotlinx.serialization.json.JsonElement = json.parseToJsonElement(text)
}
