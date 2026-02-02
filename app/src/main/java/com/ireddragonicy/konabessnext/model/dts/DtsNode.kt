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

    // Helpful for debugging
    override fun toString(): String {
        return name
    }
}
