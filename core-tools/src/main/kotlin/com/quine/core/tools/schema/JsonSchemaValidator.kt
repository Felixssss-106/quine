package com.quine.core.tools.schema

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** 单个字段的校验失败。`message` 是中文人话，会直接回灌给模型自纠。 */
data class FieldError(val path: String, val message: String)

sealed interface SchemaValidation {
    data object Valid : SchemaValidation

    data class Invalid(val errors: List<FieldError>) : SchemaValidation
}

/**
 * JSON Schema 子集校验器。
 *
 * 手写而非引库：M0 只需要给模型的自纠反馈，不需要完整规范；
 * 支持的子集是 `type` / `properties` / `required` / `enum` / `items` /
 * `additionalProperties` / `description` / `default`。
 *
 * 校验失败不抛异常 —— 调用方把错误文本作为工具结果回灌，让模型自己改参数。
 */
object JsonSchemaValidator {

    fun validate(schema: JsonObject, value: JsonElement?): SchemaValidation {
        val errors = mutableListOf<FieldError>()
        check(schema, value, emptyList(), errors)
        return if (errors.isEmpty()) SchemaValidation.Valid else SchemaValidation.Invalid(errors)
    }

    private fun check(
        schema: JsonObject,
        value: JsonElement?,
        path: List<String>,
        errors: MutableList<FieldError>,
    ) {
        val types = readTypes(schema)
        if (types.isNotEmpty()) {
            if (value == null || value is JsonNull) {
                if ("null" !in types) errors += FieldError(display(path), "缺少必填字段。")
                return
            }
            if (types.none { matches(it, value) }) {
                errors += FieldError(display(path), "类型不对：需要 ${types.joinToString(" 或 ")}。")
                return
            }
        }
        if (value == null || value is JsonNull) return

        (schema["enum"] as? JsonArray)?.let { allowed ->
            if (allowed.none { it == value }) {
                errors += FieldError(
                    display(path),
                    "只能填 ${allowed.joinToString(" / ") { it.toString() }}。",
                )
            }
        }

        when (value) {
            is JsonObject -> checkObject(schema, value, path, errors)
            is JsonArray -> {
                (schema["items"] as? JsonObject)?.let { itemSchema ->
                    value.forEachIndexed { index, child ->
                        check(itemSchema, child, path + "[$index]", errors)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun checkObject(
        schema: JsonObject,
        value: JsonObject,
        path: List<String>,
        errors: MutableList<FieldError>,
    ) {
        val properties = schema["properties"] as? JsonObject

        (schema["required"] as? JsonArray)?.forEach { required ->
            val name = (required as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@forEach
            if (!value.containsKey(name) || value[name] is JsonNull) {
                errors += FieldError(display(path + name), "缺少必填字段。")
            }
        }

        val additional = schema["additionalProperties"]
        val additionalAllowed = when (additional) {
            null -> true
            is JsonPrimitive -> additional.booleanOrNull != false
            else -> true
        }

        value.forEach { (key, child) ->
            val childSchema = properties?.get(key) as? JsonObject
            when {
                childSchema != null -> check(childSchema, child, path + key, errors)
                !additionalAllowed -> errors += FieldError(display(path + key), "不认识的字段。")
            }
        }
    }

    private fun readTypes(schema: JsonObject): List<String> = when (val type = schema["type"]) {
        is JsonPrimitive -> if (type.isString) listOf(type.content) else emptyList()
        is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        else -> emptyList()
    }

    private fun matches(type: String, value: JsonElement): Boolean = when (type) {
        "object" -> value is JsonObject
        "array" -> value is JsonArray
        "string" -> value is JsonPrimitive && value.isString
        "boolean" -> value is JsonPrimitive && !value.isString && value.booleanOrNull != null
        "integer" -> value is JsonPrimitive && !value.isString && isIntegral(value)
        "number" -> value is JsonPrimitive && !value.isString && value.doubleOrNull != null
        "null" -> value is JsonNull
        else -> true
    }

    /** JSON 里 `1` 与 `1.0` 都是整数。 */
    private fun isIntegral(value: JsonPrimitive): Boolean {
        value.longOrNull?.let { return true }
        val asDouble = value.doubleOrNull ?: return false
        return asDouble.isFinite() && asDouble % 1.0 == 0.0
    }

    private fun display(path: List<String>): String =
        if (path.isEmpty()) "参数" else path.joinToString(".")
}
