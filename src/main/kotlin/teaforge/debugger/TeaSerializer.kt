package teaforge.debugger

import teaforge.debugger.JsonValue.Companion.arr
import teaforge.debugger.JsonValue.Companion.bool
import teaforge.debugger.JsonValue.Companion.nullValue
import teaforge.debugger.JsonValue.Companion.num
import teaforge.debugger.JsonValue.Companion.obj
import teaforge.debugger.JsonValue.Companion.str
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

/**
 * Serializes TEA types (Model, Message, Effect, Subscription) to JsonValue.
 * Handles data classes, sealed interfaces, data objects, enums, and common types.
 */
object TeaSerializer {
    /**
     * Serialize any value to JsonValue.
     * Handles special cases for TEA types and common Kotlin types.
     */
    fun serialize(value: Any?): JsonValue {
        if (value == null) return nullValue

        return when (value) {
            // Primitives
            is String -> str(value)
            is Boolean -> bool(value)
            is Number -> num(value)
            is Char -> str(value.toString())

            // Collections
            is List<*> -> arr(value.map { serialize(it) })
            is Set<*> -> arr(value.map { serialize(it) })
            is Array<*> -> arr(value.map { serialize(it) })

            // Maps
            is Map<*, *> -> serializeMap(value)

            // Enums
            is Enum<*> -> serializeEnum(value)

            // Handle common WPILib types
            else -> serializeObject(value)
        }
    }

    /**
     * Serialize a map to JsonValue.
     * For maps with ADT keys, uses qualified type name as key.
     */
    private fun serializeMap(map: Map<*, *>): JsonValue {
        val entries =
            map.entries.associate { (key, value) ->
                val keyStr =
                    when (key) {
                        is String -> key
                        is Enum<*> -> "${key::class.simpleName}.${key.name}"
                        else -> {
                            // For data objects and other types, use qualified name
                            val kClass = key?.let { it::class }
                            if (kClass != null && isDataObject(kClass)) {
                                getQualifiedTypeName(key)
                            } else {
                                key?.toString() ?: "null"
                            }
                        }
                    }
                keyStr to serialize(value)
            }
        return JsonValue.JsonObject(entries)
    }

    /**
     * Serialize an enum value.
     */
    private fun serializeEnum(value: Enum<*>): JsonValue {
        return obj("_type" to str("${value::class.simpleName}.${value.name}"))
    }

    /**
     * Serialize an object (data class, data object, or regular class).
     */
    private fun serializeObject(value: Any): JsonValue {
        val kClass = value::class

        // Check if it's a data object (object with no properties to serialize)
        if (isDataObject(kClass)) {
            return obj("_type" to str(getQualifiedTypeName(value)))
        }

        // Check if it's a data class
        if (kClass.isData) {
            return serializeDataClass(value, kClass)
        }

        // For other objects, try to serialize their properties
        return serializeReflectively(value, kClass)
    }

    /**
     * Serialize a data class with all its properties.
     */
    private fun serializeDataClass(
        value: Any,
        kClass: KClass<*>,
    ): JsonValue {
        val typeName = getQualifiedTypeName(value)
        val properties = getDataClassProperties(value, kClass)

        val entries = mutableMapOf<String, JsonValue>("_type" to str(typeName))

        for ((name, propValue) in properties) {
            if (isFunction(propValue)) {
                entries[name] = serializeFunction(propValue)
            } else {
                entries[name] = serialize(propValue)
            }
        }

        return JsonValue.JsonObject(entries)
    }

    /**
     * Get all properties from a data class.
     */
    private fun getDataClassProperties(
        value: Any,
        kClass: KClass<*>,
    ): Map<String, Any?> {
        val properties = mutableMapOf<String, Any?>()

        // Use primary constructor parameters for ordering
        val constructor = kClass.primaryConstructor
        if (constructor != null) {
            for (param in constructor.parameters) {
                val name = param.name ?: continue
                val prop = kClass.declaredMemberProperties.find { it.name == name }
                if (prop != null) {
                    @Suppress("UNCHECKED_CAST")
                    val typedProp = prop as KProperty1<Any, *>
                    typedProp.isAccessible = true
                    properties[name] = typedProp.get(value)
                }
            }
        } else {
            // Fallback: use declared member properties
            for (prop in kClass.declaredMemberProperties) {
                @Suppress("UNCHECKED_CAST")
                val typedProp = prop as KProperty1<Any, *>
                typedProp.isAccessible = true
                properties[prop.name] = typedProp.get(value)
            }
        }

        return properties
    }

    /**
     * Check if a value is a function type.
     */
    private fun isFunction(value: Any?): Boolean {
        if (value == null) return false
        return value is Function<*>
    }

    /**
     * Serialize a function with metadata.
     */
    private fun serializeFunction(value: Any?): JsonValue {
        if (value == null) return nullValue
        val functionId = "fn_${value.hashCode().toString(16)}"
        return obj(
            "_functionId" to str(functionId),
            "_signature" to str(value::class.simpleName ?: "Function"),
        )
    }

    /**
     * Check if a class is a data object (Kotlin object declaration).
     */
    private fun isDataObject(kClass: KClass<*>): Boolean {
        return kClass.objectInstance != null
    }

    /**
     * Get the qualified type name for an object.
     * For nested classes, includes parent class names.
     */
    fun getQualifiedTypeName(value: Any): String {
        val kClass = value::class
        if (kClass.simpleName == null) return "Unknown"

        // Build qualified name from enclosing classes
        val parts = mutableListOf<String>()
        var currentClass: Class<*>? = kClass.java

        while (currentClass != null) {
            val name = currentClass.simpleName
            if (name.isNotEmpty()) {
                parts.add(0, name)
            }
            currentClass = currentClass.enclosingClass
        }

        return parts.joinToString(".")
    }

    /**
     * Serialize an object reflectively using its properties.
     */
    private fun serializeReflectively(
        value: Any,
        kClass: KClass<*>,
    ): JsonValue {
        val typeName = getQualifiedTypeName(value)
        val entries = mutableMapOf<String, JsonValue>("_type" to str(typeName))

        try {
            for (prop in kClass.declaredMemberProperties) {
                @Suppress("UNCHECKED_CAST")
                val typedProp = prop as KProperty1<Any, *>
                typedProp.isAccessible = true
                val propValue = typedProp.get(value)

                if (isFunction(propValue)) {
                    entries[prop.name] = serializeFunction(propValue)
                } else {
                    entries[prop.name] = serialize(propValue)
                }
            }
        } catch (e: Exception) {
            // If reflection fails, just return the type
            entries["_error"] = str("Failed to serialize: ${e.message}")
        }

        return JsonValue.JsonObject(entries)
    }

    /**
     * Get a property value by name using reflection.
     */
    private fun getPropertyValue(
        value: Any,
        propertyName: String,
    ): Any? {
        val kClass = value::class
        val prop = kClass.declaredMemberProperties.find { it.name == propertyName }
        if (prop != null) {
            @Suppress("UNCHECKED_CAST")
            val typedProp = prop as KProperty1<Any, *>
            typedProp.isAccessible = true
            return typedProp.get(value)
        }
        return null
    }

    /**
     * Serialize a wrapped message (like Message.Swerve containing SwerveSubsystem.Message).
     * Includes the _inner field for easier debugging.
     */
    fun serializeMessage(value: Any): JsonValue {
        val kClass = value::class

        if (!kClass.isData) {
            return serialize(value)
        }

        val typeName = getQualifiedTypeName(value)
        val entries = mutableMapOf<String, JsonValue>("_type" to str(typeName))

        // Get the inner message if this is a wrapper type
        val innerMessage = getPropertyValue(value, "message")
        if (innerMessage != null) {
            entries["_inner"] = serialize(innerMessage)
        }

        // Add all properties
        val properties = getDataClassProperties(value, kClass)
        for ((name, propValue) in properties) {
            if (name != "message" || innerMessage == null) {
                entries[name] = serialize(propValue)
            }
        }

        return JsonValue.JsonObject(entries)
    }
}
