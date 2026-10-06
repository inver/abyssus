package net.nevinsky.abyssus.app.game.controlline.flight

/** How a flight went, as GAME OVER shows it. */
data class FlightReport(val plane: String, val end: FlightEnd, val score: Int, val laps: Int, val bestCombo: Int, val flightTime: Float)

