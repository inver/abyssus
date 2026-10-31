/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.ui

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/**
 * The file a user picks in IntelliJ's file chooser, or null when the chooser is cancelled. A UI test cannot confirm a
 * path typed into that chooser reliably (a valid archive is opened rather than selected), so it sets the system property
 * [OVERRIDE_PROPERTY] to an absolute path first: the next call takes that file without showing the chooser and clears the
 * property, so one setting serves one pick. Nothing sets the property in a user's IDE.
 */
internal fun chooseFileOrOverride(descriptor: FileChooserDescriptor, project: Project?): VirtualFile? {
    System.getProperty(OVERRIDE_PROPERTY)?.takeIf { it.isNotBlank() }?.let { path ->
        System.clearProperty(OVERRIDE_PROPERTY)
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
    }
    return FileChooser.chooseFile(descriptor, project, null)
}

/** The system property a UI test sets to the absolute path of the file the next chooser pick returns. */
const val OVERRIDE_PROPERTY = "abyssus.test.chosenFile"
