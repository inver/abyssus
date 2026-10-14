package net.nevinsky.abyssus.uitesting.screenshot

/** The screenshot scenarios of the design inventory (design.md, decision 3). A baseline exists for each, and for nothing else. */
val SCREENSHOT_SCENARIOS: Set<String> = linkedSetOf(
    "tree-expanded",
    "tree-fog-off",
    "props-empty",
    "props-asset-model",
    "props-asset-terrain",
    "props-asset-sky-cube",
    "props-asset-sky-procedural",
    "props-asset-sky-hdr",
    "props-entity",
    "props-camera",
    "props-camera-error",
    "rename-scene-dialog",
    "skybox-chooser",
    "skybox-chooser-hdr",
    "skybox-chooser-nomatch",
)
