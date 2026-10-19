# Verification still requiring the sandbox IDE

Task 7.1 implementation and affected action/property tests passed. Its checkbox remains open until these real-IDE
checks are performed on a disposable copy of a native project. Never use the committed Untitled fixture itself.
Launch with `./gradlew :plugin-abyssus:runIde -PideProject=<absolute path to copy>`.

1. On a scene row, open Add Light; confirm Directional, Point and Spot choices appear and dismiss it.
2. On the same row, open Add Asset; confirm model/terrain sections and literal folder names, including underscores.
3. Open Add Path Arrangement; confirm the model choices appear.
4. On a terrain entity, open Add Foliage; confirm the available matching foliage choices appear.
5. Open Add Component on an entity and on the scene's ECS row; confirm the relevant choices appear.
6. On a scene row, open Add Water; confirm Lake and Sea appear.
7. Select an entity in Properties and click Add Component; confirm its menu appears underneath the button.

Task 7.4 routing/disposal tests and the pane/Game UI tests passed. Its real-IDE gate is still open:

8. With the Abyssus tree and footer visible, edit a scene and asset metadata externally; confirm the tree and counts refresh.
9. Rename a native document to another extension and back, then create/remove an asset folder externally; confirm refresh and selection remain correct.
10. Open a Game UI asset, change an image/font inside its resources externally, and confirm its preview reloads; an unrelated file must not rebuild the tree.
11. Close the Game UI editor and project; repeat an external edit after reopening and confirm there is one refresh and no disposed-consumer error.
