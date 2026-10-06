# Stable native regression inputs

This snapshot pins the original nine scene entities and nine asset identities independently of the interactive
Untitled fixture. Tree, asset-listing, ECS and scene-content assertions keep their original expected values.

Only native JSON documents are included. Runtime scene loading resolves asset identities without loading their
binary payloads. The real-ray regression uses this scene with the binary assets under Untitled.

Do not open this folder in the sandbox IDE. Use a disposable project copy for interactive checks.
