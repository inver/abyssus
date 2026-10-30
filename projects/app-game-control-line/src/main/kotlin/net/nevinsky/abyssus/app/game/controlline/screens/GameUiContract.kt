/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

/** The folder of the UI asset in the bundled project that holds every screen of the game. */
const val SCREENS_ASSET = "screens"

/** The Watch AI button of plane select: Enter on it watches the bot instead of flying. */
const val WATCH_AI_BUTTON = "ps-watch-ai"

/** Screen and overlay ids of the UI asset, as the game shows them. */
const val SCREEN_MAIN_MENU = "mainMenu"
const val SCREEN_ASSIST = "assistSelect"
const val SCREEN_PLANE = "planeSelect"
const val SCREEN_HUD_FREE = "hudFree"
const val SCREEN_HUD_PATTERN = "hudPattern"
const val SCREEN_PAUSED = "paused"
const val SCREEN_NAME_ENTRY = "nameEntry"
const val SCREEN_GAME_OVER = "gameOver"
const val SCREEN_SCORES = "scores"
const val SCREEN_DEBRIEF = "debrief"

val SCREEN_IDS = listOf(
    SCREEN_MAIN_MENU, SCREEN_ASSIST, SCREEN_PLANE, SCREEN_HUD_FREE, SCREEN_HUD_PATTERN, SCREEN_PAUSED,
    SCREEN_NAME_ENTRY, SCREEN_GAME_OVER, SCREEN_SCORES, SCREEN_DEBRIEF,
)

/**
 * The bindings the game publishes every update: the contract between the asset and [GameUiHost]. A list's fields are
 * given after a colon. `playerName` is the only one the UI writes.
 */
val PUBLISHED_BINDINGS: Map<String, String> = mapOf(
    // main menu
    "freeEnabled" to "boolean", "patternEnabled" to "boolean", "hasMessage" to "boolean", "message" to "string",
    // plane select
    "hasModeLabel" to "boolean", "modeLabel" to "string", "planeCount" to "string", "planeName" to "string", "planeFacts" to "string",
    "watchAiVisible" to "boolean",
    // who flies: an AI demonstration on the HUD, the pause menu, GAME OVER and the debrief, or the player
    "isAi" to "boolean", "isHuman" to "boolean",
    // the HUD
    "tensionText" to "string", "tensionValue" to "number", "fuel" to "string", "laps" to "string", "score" to "string",
    "combo" to "string", "time" to "string", "next" to "string", "pauseLaps" to "string", "warning" to "string",
    "prompted" to "boolean", "toast" to "string", "toastVisible" to "boolean",
    // the flight summary (name entry and game over)
    "endLabel" to "string", "isFree" to "boolean", "isPattern" to "boolean", "patternHeader" to "string", "sheetScore" to "string",
    "facts" to "list:name,value", "sheet" to "list:title,k,result,style",
    "playerName" to "string", "rankText" to "string", "hasRank" to "boolean", "canDebrief" to "boolean",
    // the score tables
    "tabFreeStyle" to "string", "tabProStyle" to "string", "tabMidStyle" to "string", "tabNoobStyle" to "string",
    "scoresHint" to "string", "damagedMessage" to "string", "hasDamaged" to "boolean", "isFreeTab" to "boolean", "isBoardTab" to "boolean",
    "freeRows" to "list:rank,name,plane,score,laps,combo,time,date,style",
    "boardRows" to "list:rank,name,plane,score,time,date,style",
    // the debrief
    "debriefTitle" to "string", "debriefRows" to "list:label,k,result,style,kStyle", "hasRecording" to "boolean",
    "deductionTitle" to "string", "deductions" to "list:text,style", "hasNotice" to "boolean", "notice" to "string",
    "debriefStatus" to "string", "debriefKeys" to "string",
)

/** The named actions the asset emits; `openRow` arrives as `openRow#<row>` from the board rows. */
val ACTIONS = listOf(
    "freeFlight", "f2bPattern", "scores", "quit", "assistPro", "assistMid", "assistNoob", "back", "previousPlane", "nextPlane",
    "choosePlane", "watchAi", "resume", "mainMenu", "saveName", "retry", "debrief", "tabFree", "tabPro", "tabMid", "tabNoob", "openRow",
)
