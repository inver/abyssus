package net.nevinsky.abyssus.runtime.json

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.DecimalNode
import java.math.BigDecimal
import kotlin.math.abs

/** A float as the scene file writes it: whole numbers without a fraction, others with the shortest float text. */
fun number(value: Float): JsonNode {
    val v = if (value.isFinite()) value else 0f
    return if (v == Math.rint(v.toDouble()).toFloat() && abs(v) < 1e9f) IntNode.valueOf(v.toInt()) else DecimalNode(
        BigDecimal(v.toString())
    )
}

