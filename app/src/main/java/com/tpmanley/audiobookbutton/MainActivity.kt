package com.tpmanley.audiobookbutton

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Edit screen: choose which audiobook the widget plays. */
class MainActivity : ComponentActivity() {

    private var sharedText by mutableStateOf<String?>(null)
    private var lastResult by mutableStateOf<String?>(null)

    override fun onResume() {
        super.onResume()
        lastResult = BookStore.lastResult(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedText = extractShared(intent)
        val saved = BookStore.load(this)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    EditScreen(
                        initial = saved,
                        sharedText = sharedText,
                        lastResult = lastResult,
                        onSave = ::save,
                        onTest = { book -> save(book); startActivity(Intent(this, PlayActivity::class.java)) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractShared(intent)?.let { sharedText = it }
    }

    private fun extractShared(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null

    private fun save(book: Book) {
        BookStore.save(this, book)
        AudiobookWidgetProvider.refreshAll(this)
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun EditScreen(
    initial: Book?,
    sharedText: String?,
    lastResult: String?,
    onSave: (Book) -> Unit,
    onTest: (Book) -> Unit,
) {
    var link by remember { mutableStateOf(initial?.uri.orEmpty()) }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var titleEdited by remember { mutableStateOf(initial?.title?.isNotBlank() == true) }

    // Pre-fill from "Share → Audiobook Button".
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            link = sharedText
            titleEdited = false
        }
    }

    val uri = SpotifyLink.toUri(link)

    // Auto-fill the title when a new valid link appears, unless the user typed one.
    LaunchedEffect(uri) {
        if (uri != null && uri != initial?.uri && !titleEdited) {
            OEmbed.fetchTitle(uri)?.let { if (!titleEdited) title = it }
        }
    }

    Column(
        Modifier.safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.edit_heading), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.edit_help), style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            label = { Text(stringResource(R.string.link_label)) },
            isError = link.isNotBlank() && uri == null,
            supportingText = {
                when {
                    link.isBlank() -> {}
                    uri == null -> Text(stringResource(R.string.link_invalid))
                    else -> Text(uri)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; titleEdited = true },
            label = { Text(stringResource(R.string.title_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = uri != null, onClick = { onSave(Book(uri!!, title.trim())) }) {
                Text(stringResource(R.string.save))
            }
            OutlinedButton(enabled = uri != null, onClick = { onTest(Book(uri!!, title.trim())) }) {
                Text(stringResource(R.string.test_play))
            }
        }
        Text(stringResource(R.string.widget_hint), style = MaterialTheme.typography.bodySmall)
        if (lastResult != null) {
            Text(stringResource(R.string.last_result), style = MaterialTheme.typography.titleSmall)
            SelectionContainer { Text(lastResult, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
