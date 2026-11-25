package com.dipesh.ai

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotifItem(val key: String, val app: String, val title: String, val text: String, val time: Long)

/**
 * Lets the user read and clear their notifications through DIPESH AI.
 * It only works after the user switches on "Notification access" for the app in Android settings.
 */
class NotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        _recent.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val active = try {
            activeNotifications
        } catch (e: Exception) {
            null
        } ?: return
        _recent.value = active
            .filter { it.packageName != packageName && it.isClearable }
            .sortedByDescending { it.postTime }
            .take(30)
            .map { toItem(it) }
    }

    private fun toItem(s: StatusBarNotification): NotifItem {
        val extras = s.notification.extras
        val app = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(s.packageName, 0)).toString()
        } catch (e: Exception) {
            s.packageName
        }
        return NotifItem(
            key = s.key,
            app = app,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            time = s.postTime
        )
    }

    companion object {
        @Volatile
        var instance: NotificationService? = null

        private val _recent = MutableStateFlow<List<NotifItem>>(emptyList())
        val recent: StateFlow<List<NotifItem>> = _recent.asStateFlow()
    }
}
