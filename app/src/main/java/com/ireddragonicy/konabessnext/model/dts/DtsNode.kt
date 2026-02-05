package com.ireddragonicy.konabessnext.model.dts

import java.util.ArrayList

/**
 * Represents a Node in the DTS tree.
 * Example: node_name { ... };
 */
class DtsNode(name: String?) {
    @JvmField
    var name: String = name?.trim() ?: "root"
    
    @JvmField
    var children: MutableList<DtsNode> = ArrayList()
    
    @JvmField
    var properties: MutableList<DtsProperty> = ArrayList()
    
    @JvmField
    var isExpanded: Boolean = false // Default collapsed

    @JvmField
    var parent: DtsNode? = null

    fun addChild(child: DtsNode) {
        child.parent = this
        children.add(child)
    }

    fun addProperty(prop: DtsProperty) {
        properties.add(prop)
    }

    fun getFullPath(): String {
        if (parent == null || parent?.name == "root") {
            return name
        }
        val parentPath = parent!!.getFullPath()
        if (parentPath == "/") {
            return "/$name"
        }
        return "$parentPath/$name"
    }

    fun getProperty(name: String): DtsProperty? {
        return properties.find { it.name == name }
    }

    fun getChild(name: String): DtsNode? {
        return children.find { it.name == name }
    }

    fun getChildrenByPrefix(prefix: String): List<DtsNode> {
        return children.filter { it.name.startsWith(prefix) }
    }

    fun getLongValue(propName: String): Long? {
        val prop = getProperty(propName) ?: return null
        return com.ireddragonicy.konabessnext.utils.DtsHelper.extractLongValue(prop.name + " = " + prop.originalValue + ";")
    }

    fun setProperty(name: String, value: String) {
        val existingProp = getProperty(name)
        if (existingProp != null) {
            // If it's a hex array, we treat the input 'value' as a display value (e.g. decimal)
            // and format it back to hex wrapped in < >
            if (existingProp.isHexArray) {
                existingProp.updateFromDisplayValue(value)
            } else {
                existingProp.originalValue = value
            }
        } else {
            // New property
            val newProp = DtsProperty(name, value)
            // If the value looks like a hex array description (e.g. user passed "123 456"),
            // we ironically might want to force it?
            // For now, if it's new, we assume the caller provided the RAW valid DTS value (e.g. "<0x...>").
            // OR we check if the caller wants to format it.
            // Let's assume standard behavior: caller provides raw value string unless they use a specific helper.
            // However, consistent with update behavior, if the value passed is NOT wrapped in <> but SHOULD be,
            // we rely on the caller to formatted it or DtsProperty to handle it.
            // DtsProperty constructor takes (name, originalValue).
            addProperty(newProp)
        }
    }

    // Helpful for debugging
    override fun toString(): String {
        return name
    }
}
