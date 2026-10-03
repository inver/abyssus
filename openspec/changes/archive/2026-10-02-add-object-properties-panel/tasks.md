# Tasks

## 1. Meta row model

- [x] 1.1 Add `PropertyRow` and pure `metaRowsOf(JsonNode)` in a new `properties/` package, with bundle strings for list/object summaries; verify with unit tests on the `skybox_default`, `model_*` and `terrain_*` `meta.json` samples (`./gradlew test`): field order, `additional` grouping, `null`, empty list, missing `uuid`, formatted `lastModified`
- [x] 1.2 Add loading of an asset's `meta.json` (editor text first, then disk) returning rows or a readable error; verify with unit tests for a valid file, a missing file and a malformed file

## 2. Selection publishing

- [x] 2.1 Add the `AbyssusSelectionListener` topic and publish the selected user object from `AbyssusProjectViewPane`'s tree selection listener; verify with a platform test that selecting a node fires the topic once and clearing the selection fires `null`
- [x] 2.2 Resolve a selected `AssetInfo` entry to its asset folder's `meta.json`; verify with a test that an asset row resolves and a scene, a project file and a property row do not

## 3. Tool window

- [x] 3.1 Register the `Abyssus Properties` tool window in `plugin.xml` (right anchor, DumbAware factory) with the scroll-pane layout: Name / Value grid with the `additional` heading row, and the empty state (`Nothing selected.` / `Nothing to show: <name> is ...` plus the hint); verify with a platform test that it is registered and shows the empty message
- [x] 3.1a Add the header block (type icon with generic fallback, name, `<type> asset · read-only`); verify with a test for a skybox and for an unknown type
- [x] 3.2 Subscribe the panel to the selection topic and keep the last selection; verify the table shows a skybox's Meta after selecting its row, including when the panel opens after the selection, and the "cannot read Meta" message for a broken `meta.json`
- [x] 3.2a Add skybox "Face previews" (six labelled thumbnails decoded off the EDT, placeholder for a missing or unreadable file, section absent for other types); verify with tests on `skybox_default` (six images) and a skybox with one missing face
- [x] 3.3 Refresh on `meta.json` change (VFS and document edits); verify by editing `additional.size` in a test and observing the new value (and a changed face file updating its preview)

## 4. Wrap-up

- [x] 4.1 Document the panel in the plugin's docs/README and run the plugin via `./gradlew runIde` to confirm that selecting skybox, model and terrain assets fills the panel (header, rows, face previews for a skybox) and selecting a scene shows the empty message, comparing against the "Panels" board of the design canvas
