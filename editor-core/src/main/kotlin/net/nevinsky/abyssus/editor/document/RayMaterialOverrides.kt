/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.editor.document

import net.nevinsky.abyssus.editor.document.RayDataEdit
import net.nevinsky.abyssus.editor.document.RayDataError
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

/** IDs refer to a single model's material table, never a shared asset editor. */
data class RayMaterialIdentity(val id: String?, val pbr: Boolean)
data class RayOpticalOverride(val transmission: Double = 0.0, val ior: Double = 1.5)
enum class RayOpticalField(val key: String,val default: Double,val minimum: Double,val maximum: Double) {
    TRANSMISSION("transmission",0.0,0.0,1.0), IOR("ior",1.5,1.0,3.0),
}
data class RayMaterialOverrideState(val values: Map<String,RayOpticalOverride>,val errors: Map<String,RayDataError>)

/** Pure codec/editor of a RenderComponent's optical data; unresolved entries stay in the document. */
class RayMaterialOverrides {
    fun read(render: JsonNode): RayMaterialOverrideState {
        val values=linkedMapOf<String,RayOpticalOverride>()
        val errors=linkedMapOf<String,RayDataError>()
        val map=render.get("rayTracingMaterials") ?: return RayMaterialOverrideState(values,errors)
        if(!map.isObject) return RayMaterialOverrideState(values,mapOf("rayTracingMaterials" to RayDataError.OBJECT))
        for((id,block) in map.properties()) {
            if(id.isBlank()) { errors[id]=RayDataError.MATERIAL_ID; continue }
            if(!block.isObject) { errors[id]=RayDataError.OBJECT;continue }
            val fields=RayOpticalField.entries.associateWith { field ->
                val node=block.get(field.key)
                val error=validate(node,field)
                if(error!=null) errors["$id.${field.key}"]=error
                if(node==null || error!=null) field.default else node.doubleValue()
            }
            values[id]=RayOpticalOverride(fields.getValue(RayOpticalField.TRANSMISSION),fields.getValue(RayOpticalField.IOR))
        }
        return RayMaterialOverrideState(values,errors)
    }

    fun eligible(id: String,materials: List<RayMaterialIdentity>): RayDataError? {
        if(id.isBlank()) return RayDataError.MATERIAL_ID
        val matches=materials.filter { it.id==id }
        if(matches.size!=1) return RayDataError.MATERIAL_ID
        return if(matches.single().pbr) null else RayDataError.NON_PBR
    }
    fun unresolved(render: JsonNode,materials: List<RayMaterialIdentity>): Set<String> =
        read(render).values.keys.filterTo(linkedSetOf()) { eligible(it,materials)!=null }

    fun edit(render: ObjectNode,id: String,field: RayOpticalField,expected: JsonNode?,text: String,materials: List<RayMaterialIdentity>): RayDataEdit {
        eligible(id,materials)?.let { return RayDataEdit.Rejected(it) }
        val map=render.get("rayTracingMaterials")
        if(map!=null && !map.isObject) return RayDataEdit.Rejected(RayDataError.OBJECT)
        val block=map?.get(id)
        if(block!=null && !block.isObject) return RayDataEdit.Rejected(RayDataError.OBJECT)
        val actual=block?.get(field.key)
        if(actual!=expected) return RayDataEdit.Conflict
        val value=text.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: return RayDataEdit.Rejected(RayDataError.NUMBER)
        if(value !in field.minimum..field.maximum) return RayDataEdit.Rejected(RayDataError.RANGE)
        if((actual==null && value==field.default) || (validate(actual,field)==null && actual?.doubleValue()==value)) return RayDataEdit.Unchanged
        if(value==field.default) {
            (block as? ObjectNode)?.remove(field.key)
            if(block!=null && block.isEmpty) (map as ObjectNode).remove(id)
            if(map!=null && map.isEmpty) render.remove("rayTracingMaterials")
        } else {
            val targetMap=map as? ObjectNode ?: render.objectNode().also { render.set<ObjectNode>("rayTracingMaterials",it) }
            val target=block as? ObjectNode ?: render.objectNode().also { targetMap.set<ObjectNode>(id,it) }
            target.put(field.key,value)
        }
        return RayDataEdit.Changed
    }
    private fun validate(node: JsonNode?,field: RayOpticalField): RayDataError? = when {
        node==null -> null
        !node.isNumber || !node.doubleValue().isFinite() -> RayDataError.NUMBER
        node.doubleValue() !in field.minimum..field.maximum -> RayDataError.RANGE
        else -> null
    }
}
