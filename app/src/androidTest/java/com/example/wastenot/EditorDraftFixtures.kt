package com.example.wastenot

import android.content.Context
import java.io.File

internal fun clearEditorDraftFixtures(context: Context) {
    File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
        .filter { it.name.startsWith("editor_draft_") && it.extension == "xml" }
        .forEach { file ->
            val preferences = context.getSharedPreferences(file.nameWithoutExtension, Context.MODE_PRIVATE)
            synchronized(preferences) { check(preferences.edit().clear().commit()) }
        }
}
