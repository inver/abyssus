package net.nevinsky.abyssus.lib.core.dto

import com.badlogic.ashley.core.Component

data class EntityDto(val components: List<out Component>)