package com.roserequiem.app.ui.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.roserequiem.app.R

/**
 * Plain-text record of notable fixes, kept in sync with CHANGELOG.md by hand.
 * Update this alongside CHANGELOG.md whenever a fix lands.
 */
private val FIX_HISTORY = """
    Version 4.4.0

    • A second batch download could start while one was already running, corrupting shared progress state and notifications.

    • Build failure on Kotlin 2.2+: the kotlinOptions {} DSL was removed at that version. Migrated to compilerOptions.

    • Build failure from the Coil 2→3 migration: MemoryCache.Builder, DiskCache.Builder.directory(), and the image loader's placeholder/error setters all changed signature in Coil 3.5.0. Updated both image loaders and the composables that set placeholders to match.

    • Build failure in the quick-search image loader, also from the Coil 2→3 migration: allowHardware, crossfade, and bitmapFactoryMaxParallelism needed their own imports in Coil 3.5.0, and dispatcher() was renamed to coroutineContext().
""".trimIndent()

@Composable
fun ChangelogDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fix_history)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(FIX_HISTORY)
            }
        },
        confirmButton = {
            OutlinedButton(onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}
