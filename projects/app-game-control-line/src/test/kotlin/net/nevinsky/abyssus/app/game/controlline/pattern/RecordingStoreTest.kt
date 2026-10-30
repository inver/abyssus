/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class RecordingStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val folder get() = temp.root.toPath().resolve("home/.abyssus-control-line/f2b-flights")

    @Test
    fun aRoundTripKeepsTheSamplesAndTheJudgementsExactly() {
        val store = RecordingStore(folder)
        val recording = recordedFlight(AssistLevel.MID)
        val id = store.save(recording)
        assertTrue(Files.exists(folder.resolve("$id.json.gz")))
        val loaded = checkNotNull(store.load(id))
        assertEquals("Stunter", loaded.plane)
        assertEquals(AssistLevel.MID, loaded.level)
        assertEquals(recording.lineLength, loaded.lineLength, 0f)
        assertArrayEquals(recording.samples, loaded.samples, 0f)
        assertEquals(recording.judgements.size, loaded.judgements.size)
        for ((a, b) in recording.judgements.zip(loaded.judgements)) {
            assertEquals(a.index, b.index)
            assertEquals(a.start, b.start, 0f)
            assertEquals(a.end, b.end, 0f)
            assertEquals(a.tenths, b.tenths)
            assertEquals(a.reason, b.reason)
            assertEquals(a.deductions, b.deductions)
            assertArrayEquals(a.ideal, b.ideal, 0f)
        }
    }

    @Test
    fun theSamplesHaveThreeDecimals() {
        val recording = recordedFlight()
        for (v in recording.samples) assertEquals("$v", Math.round(v * 1000f) / 1000f, v, 0f)
    }

    @Test
    fun saveLeavesNoTemporaryFile() {
        val store = RecordingStore(folder)
        store.save(recordedFlight())
        assertTrue(Files.list(folder).use { files -> files.allMatch { it.fileName.toString().endsWith(".json.gz") } })
    }

    @Test
    fun pruneKeepsOnlyTheNamedIdsAndDropsLeftoverTemporaryFiles() {
        val store = RecordingStore(folder)
        val kept = store.save(recordedFlight())
        val dropped = store.save(recordedFlight())
        Files.writeString(folder.resolve("flight123.tmp"), "half a save")
        store.prune(setOf(kept))
        assertEquals(setOf(kept), store.ids())
        assertFalse(Files.exists(folder.resolve("flight123.tmp")))
        assertNull(store.load(dropped))
    }

    @Test
    fun pruningWithNoFolderMakesNone() {
        RecordingStore(folder).prune(emptySet())
        assertFalse(Files.exists(folder))
    }

    @Test
    fun textThatIsNotGzipBecomesBadAndLoadsAsNotAvailable() {
        val store = RecordingStore(folder)
        Files.createDirectories(folder)
        Files.writeString(folder.resolve("broken.json.gz"), "this is not gzip")
        assertNull(store.load("broken"))
        assertFalse(Files.exists(folder.resolve("broken.json.gz")))
        assertEquals("this is not gzip", Files.readString(folder.resolve("broken.json.gz.bad")))
    }

    @Test
    fun aMissingRecordingLoadsAsNotAvailableAndAnIdIsNeverAPath() {
        val store = RecordingStore(folder)
        assertNull(store.load("nothing"))
        assertNull(store.load("../f2b-scores"))
        assertNull(store.load(""))
    }

    @Test
    fun deleteRemovesTheFile() {
        val store = RecordingStore(folder)
        val id = store.save(recordedFlight())
        store.delete(id)
        assertTrue(store.ids().isEmpty())
        store.delete(id)
    }
}
