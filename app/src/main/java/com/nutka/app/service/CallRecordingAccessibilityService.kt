package com.nutka.app.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nutka.app.R
import com.nutka.app.data.AppLog
import com.nutka.app.data.SettingsRepository

/**
 * The piece that makes phone-call recording possible at all on a stock,
 * unrooted phone — it reads nothing on screen and reacts to no UI.
 *
 * Why an accessibility service: while a call is active (audio mode IN_CALL)
 * Android gives every ordinary app's microphone capture pure silence. The
 * platform's only exemption a non-system app can reach is being an enabled
 * accessibility service that captures from VOICE_RECOGNITION while its process
 * sits in the foreground-service band (AudioPolicyService::updateUidStates_l,
 * "isA11yOnTop"). [RecordingService] provides that foreground service; this
 * class provides the "is an accessibility service" half.
 *
 * It also doubles as the call detector: the system keeps an enabled
 * accessibility service bound (and its process alive) permanently, which a
 * manifest broadcast receiver could not guarantee, and that same binding is
 * what allows the microphone foreground service to be started from the
 * background mid-call on Android 14+.
 *
 * What it still cannot do: hear the other side through the earpiece. The
 * microphone only picks them up acoustically, so the call has to be on
 * speaker — and no third-party app can switch the call's speaker on, because
 * Telecom owns the call's audio route. [RecordingService] warns in its
 * notification whenever the speaker is off.
 */
class CallRecordingAccessibilityService : AccessibilityService() {

    private var telephonyCallback: TelephonyCallback? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var lastCallState = TelephonyManager.CALL_STATE_IDLE
    private var missingPermissionLogged = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLog.init(applicationContext)
        AppLog.d("Rozmowy", "Usługa dostępności Nutki aktywna")
        registerCallStateListener()
    }

    /**
     * Only events from Nutka's own windows arrive here (see the service's
     * packageNames filter), so this is effectively "the user just opened
     * Nutka" — the moment to retry the call listener if the phone permission
     * was granted after the service had already connected.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (telephonyCallback == null && phoneStateListener == null) registerCallStateListener()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        unregisterCallStateListener()
        callInProgress = false
        super.onDestroy()
    }

    private fun registerCallStateListener() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            // Retried on every Nutka window event, so say it once, not per event.
            if (!missingPermissionLogged) AppLog.d("Rozmowy", "Brak uprawnienia do stanu telefonu — nie wykryję rozmów")
            missingPermissionLogged = true
            return
        }
        val telephony = getSystemService(TelephonyManager::class.java) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = onCallState(state)
                }
                telephony.registerTelephonyCallback(mainExecutor, callback)
                telephonyCallback = callback
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) = onCallState(state)
                }
                @Suppress("DEPRECATION")
                telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                phoneStateListener = listener
            }
        }.onSuccess {
            AppLog.d("Rozmowy", "Nasłuchuję stanu połączeń")
        }.onFailure { e ->
            AppLog.e("Rozmowy", "Nie udało się zarejestrować nasłuchu połączeń", e)
        }
    }

    private fun unregisterCallStateListener() {
        val telephony = getSystemService(TelephonyManager::class.java) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) telephonyCallback?.let(telephony::unregisterTelephonyCallback)
            @Suppress("DEPRECATION")
            phoneStateListener?.let { telephony.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
        telephonyCallback = null
        phoneStateListener = null
    }

    private fun onCallState(state: Int) {
        val previous = lastCallState
        lastCallState = state
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                callInProgress = true
                if (previous == TelephonyManager.CALL_STATE_OFFHOOK) return
                // Read fresh from disk: the toggles may have changed in the UI
                // since this long-lived service last looked.
                val settings = SettingsRepository(this).state.value
                if (!settings.callRecording) return
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    AppLog.e("Rozmowy", "Rozmowa wykryta, ale brak uprawnienia do mikrofonu")
                    return
                }
                if (settings.autoRecordCalls) {
                    AppLog.d("Rozmowy", "Rozmowa rozpoczęta — nagrywam automatycznie")
                    runCatching {
                        ContextCompat.startForegroundService(this, RecordingService.recordCallIntent(this))
                    }.onFailure { e -> AppLog.e("Rozmowy", "Nie udało się uruchomić nagrywania rozmowy", e) }
                } else {
                    AppLog.d("Rozmowy", "Rozmowa rozpoczęta — pytam, czy nagrać")
                    showRecordPrompt(this)
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                callInProgress = false
                cancelRecordPrompt(this)
                if (RecordingService.callSessionActive) {
                    AppLog.d("Rozmowy", "Rozmowa zakończona — zatrzymuję nagrywanie")
                    runCatching {
                        startService(Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_CALL_ENDED))
                    }.onFailure { e -> AppLog.e("Rozmowy", "Nie udało się zatrzymać nagrywania rozmowy", e) }
                }
            }
            // RINGING: nothing to record yet — an incoming call only becomes a
            // conversation once it is answered, which arrives as OFFHOOK.
        }
    }

    companion object {
        private const val PROMPT_CHANNEL_ID = "calls"
        private const val PROMPT_NOTIFICATION_ID = 422

        /** True between OFFHOOK and IDLE, as last reported to the running service. */
        @Volatile
        var callInProgress: Boolean = false
            private set

        /** Whether the user has switched this service on in the system accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
        }

        /**
         * Heads-up "record this call?" prompt. Tapping it starts the recording
         * through a foreground-service PendingIntent, which is itself one of
         * Android's sanctioned ways to open the microphone from the background.
         */
        fun showRecordPrompt(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(PROMPT_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(PROMPT_CHANNEL_ID, "Rozmowy telefoniczne", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Pytanie o nagranie rozmowy, gdy się zaczyna"
                        setSound(null, null)
                        enableVibration(false)
                    }
                )
            }
            val record = PendingIntent.getForegroundService(
                context,
                PROMPT_NOTIFICATION_ID,
                RecordingService.recordCallIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(context, PROMPT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Nagrać tę rozmowę?")
                .setContentText("Włącz głośnik, żeby nagrać też rozmówcę")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(record)
                .addAction(0, "Nagraj", record)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            runCatching { manager.notify(PROMPT_NOTIFICATION_ID, notification) }
                .onFailure { e -> AppLog.e("Rozmowy", "Nie udało się pokazać pytania o nagranie", e) }
        }

        fun cancelRecordPrompt(context: Context) {
            runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(PROMPT_NOTIFICATION_ID) }
        }
    }
}
