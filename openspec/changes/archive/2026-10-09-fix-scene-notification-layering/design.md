## Context

AWTGLCanvas uses a native macOS GL layer; IntelliJ balloons are lightweight Swing siblings in the window's layered pane. Swing z-order alone does not place them above that native surface.

## Decisions

Wrap the scene canvas in a Swing host. Check intersecting visible siblings above each ancestor in Swing z-order. Capture the front framebuffer while GL is safe, hide the native canvas without removing its peer, and paint the snapshot through Swing. Restore canvas visibility after overlap ends. Use an independent Swing timer so restoration still occurs while the scene render timer skips the hidden surface. Stop monitoring when the host is removed.

Ignore empty glass panes and siblings below the scene. Capture only on entry into overlap, avoiding continuous GPU readback. Clear snapshots on restoration. New canvases receive a new host after context abandonment.

## Tradeoffs

The scene pauses visually during overlap; the notification remains readable and clickable. This avoids a permanent offscreen rendering pipeline and continuous GPU readback. Snapshot dimensions use framebuffer pixels, then scale to logical Swing bounds.

## Verification

Test overlap, dismissal, nonintersecting and lower layers. Run plugin checks. A display test must confirm native macOS hiding and restoration with a real GL context.
