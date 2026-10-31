package net.nevinsky.abyssus.lib.core.testing

import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.ecs.EcsLoader
import net.nevinsky.abyssus.lib.core.ecs.EcsLoadingWarns
import net.nevinsky.abyssus.lib.core.ecs.SceneEngine
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.SceneLoader
import org.slf4j.helpers.NOPLogger
import java.io.File

/** The scene that holds [ecs], the text of an `ecs` object (entities by id), read the way a scene file is. */
fun sceneWithEcs(
    ecs: String,
    registry: ComponentRegistry = ComponentRegistry(),
    json: JsonProcessor = JsonProcessor(NOPLogger.NOP_LOGGER),
): SceneDto = SceneLoader(json, FileLoader(File(".")), registry)
    .parse("""{"format":"abyssus","formatVersion":1,"ecs":$ecs}""")

/** Loads the `ecs` object [ecs] into [engine], reading and loading it as a scene does. */
fun loadEcs(
    ecs: String,
    engine: SceneEngine,
    registry: ComponentRegistry = ComponentRegistry(),
    json: JsonProcessor = JsonProcessor(NOPLogger.NOP_LOGGER),
): EcsLoadingWarns = EcsLoader().loadToEngine(sceneWithEcs(ecs, registry, json).ecs, engine)
