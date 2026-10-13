package com.vpr.screenlate.dictionary.api.imports

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The Cancel action of the import notification: cancels that task only, as the button in the app does. */
@AndroidEntryPoint
class ImportCancelReceiver : BroadcastReceiver() {
    @Inject lateinit var imports: DictionaryImports

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_TASK)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                imports.cancel(id)
            } catch (e: Exception) {
                Log.w("DictionaryImports", "Cancelling from the notification failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_TASK = "task"

        fun intent(context: Context, id: UUID): PendingIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            Intent(context, ImportCancelReceiver::class.java).putExtra(EXTRA_TASK, id.toString()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
