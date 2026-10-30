# Sandbox verification still required

## Prepared artifacts — 2026-10-06

`./gradlew :buildPlugin :physics-plugin:buildPlugin --console=plain` passes. The ZIPs are ready at:

- `build/distributions/abyssus-0.0.1.zip` (44,849,596 bytes), SHA-256
  `c12a52c0d2dd2034878441ee1da6f2c81cf7db9aad73b0afa1ba75cda47edab0`.
- `physics-plugin/build/distributions/physics-plugin.zip` (38,095,736 bytes), SHA-256
  `91d31b0c19e3b58b88b26db928a5f623dd1d2acf6e3d4d57718bafd7f84da540`.

Packaging inspection confirms Abyssus Physics has exactly its plugin JAR and `physics.jar` under `lib/`;
the separate play-host dependencies remain outside the IDE classpath. Test-only TinyEXR native JARs are absent
from both ZIPs. These checks establish packaging, not successful dynamic installation or unloading.

A disposable project is prepared at `/private/tmp/abyssus-solid-manual-2m6v8d7d/Untitled`, using real Untitled
binary assets and the stable Tree project/scene documents. Both documents have native version 1 identity.
Committed fixtures were not changed. To open this copy with both plugins available, run:

```sh
./gradlew :physics-plugin:runIde -PideProject=/private/tmp/abyssus-solid-manual-2m6v8d7d/Untitled
```

Temporary paths may be removed by the operating system; recreate the copy if it no longer exists.

## Manual procedure

1. Run `./gradlew buildPlugin :physics-plugin:buildPlugin`. Copy a native test project to a disposable directory
   outside the committed fixtures; never open `src/test/testData/project/Untitled` or the committed Control Line project.
2. Run `./gradlew runIde -PideProject=/absolute/path/to/the/copy`. In the Abyssus tree, check Rename Scene...,
   New Terrain..., Add Light, Add Component... and Remove Component. Confirm the Abyssus Properties stripe
   and notification group titles retain their English text. Open the skybox chooser and wait for thumbnails.
3. In Settings > Plugins > gear > Install Plugin from Disk, install the built Abyssus zip and the Abyssus Physics
   zip from each module's build/distributions directory. Record whether the IDE requests a restart.
4. Install a rebuilt zip over each installed plugin and record whether dynamic update succeeds. Disable/uninstall
   Abyssus Physics first, then Abyssus; record unload results and any restart request. Reinstall both for the next step.
5. Open a scene view, switch ray tracing on where supported, and close the project. Use Help > Show Log in Finder
   to inspect idea.log for DynamicPlugins unload errors, disposer errors and leaked abyssus-ray/convert threads.
6. Report IDE build, OS, each result, and relevant log excerpts. A restart requirement or disposal error leaves
   task 10.9 open until investigated; this checklist is not evidence that dynamic reload passed.

CI task 10.2 also remains unverified until a pull-request workflow completes and uploads pluginVerifier-result.
The local workflow edits are reviewable without pushing this mixed workspace.
