package com.reelguard.app.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

data class QuotaStatus(
    val isExceeded: Boolean = false,
    val isWarning: Boolean = false,
    val countExceeded: Boolean = false,
    val timeExceeded: Boolean = false,
    val scheduledBlocked: Boolean = false,
    val focusModeActive: Boolean = false,
    val countLimit: Int = -1,
    val countUsed: Int = 0,
    val countRemaining: Int = -1,
    val timeLimitMin: Int = -1,
    val timeUsedMs: Long = 0,
    val timeRemainingMs: Long = -1,
    val scheduleMessage: String = "",
    val focusEndTime: String = "",
    val streakDays: Int = 0
)

class QuotaManager private constructor(private val context: Context) {

    companion object {
        @Volatile private var instance: QuotaManager? = null

        fun getInstance(context: Context): QuotaManager =
            instance ?: synchronized(this) {
                instance ?: QuotaManager(context.applicationContext).also { instance = it }
            }

        // Clés SharedPreferences
        const val PREF_FILE = "reelguard_prefs"

        // Quota par nombre
        const val KEY_COUNT_ENABLED = "quota_count_enabled"
        const val KEY_COUNT_LIMIT = "quota_count_limit"
        const val KEY_COUNT_TODAY = "quota_count_today"

        // Quota par temps
        const val KEY_TIME_ENABLED = "quota_time_enabled"
        const val KEY_TIME_LIMIT_MIN = "quota_time_limit_min"
        const val KEY_TIME_TODAY_MS = "quota_time_today_ms"

        // Date de dernier reset
        const val KEY_LAST_RESET_DATE = "last_reset_date"
        const val KEY_RESET_HOUR = "reset_hour"

        // Plages horaires
        const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        const val KEY_SCHEDULE_RULES = "schedule_rules"

        // Mode Focus
        const val KEY_FOCUS_END_TIME = "focus_end_time"

        // PIN anti-contournement
        const val KEY_PIN_ENABLED = "pin_enabled"
        const val KEY_PIN_HASH = "pin_hash"

        // Streak
        const val KEY_STREAK_DAYS = "streak_days"
        const val KEY_LAST_STREAK_DATE = "last_streak_date"
        const val KEY_QUOTA_MET_TODAY = "quota_met_today"

        // Apps activées (préfixe + package)
        const val KEY_APP_PREFIX = "app_enabled_"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    private val _quotaState = MutableStateFlow(getCurrentQuotaStatus())
    val quotaState: StateFlow<QuotaStatus> = _quotaState

    // Compteur en mémoire pour la session courante (complète le compteur persisté)
    private val sessionReelCount = AtomicInteger(0)
    private var sessionTimeMs = 0L
    private var sessionStartTime = 0L

    init {
        checkAndResetDailyQuotas()
    }

    // ----------------------------------------------------------------
    // VÉRIFICATION ET CONSOMMATION DU QUOTA
    // ----------------------------------------------------------------

    fun checkAndConsumeQuota(packageName: String): QuotaStatus {
        checkAndResetDailyQuotas()

        // Vérifier mode Focus
        if (isFocusModeActive()) {
            val status = QuotaStatus(
                isExceeded = true,
                focusModeActive = true,
                focusEndTime = getFocusEndTimeDisplay(),
                streakDays = getStreakDays()
            )
            _quotaState.value = status
            return status
        }

        // Vérifier plage horaire bloquée
        if (isScheduleBlocked()) {
            val status = QuotaStatus(
                isExceeded = true,
                scheduledBlocked = true,
                scheduleMessage = getScheduleBlockMessage(),
                streakDays = getStreakDays()
            )
            _quotaState.value = status
            return status
        }

        // Vérifier quota en nombre
        if (isCountQuotaEnabled()) {
            val limit = getCountLimit()
            val used = prefs.getInt(KEY_COUNT_TODAY, 0)
            if (used >= limit) {
                val status = QuotaStatus(
                    isExceeded = true,
                    countExceeded = true,
                    countLimit = limit,
                    countUsed = used,
                    countRemaining = 0,
                    streakDays = getStreakDays()
                )
                _quotaState.value = status
                return status
            }
            // Avertissement à 80%
            val isWarning = used >= (limit * 0.8).toInt()
            if (isWarning) {
                val status = QuotaStatus(
                    isWarning = true,
                    countLimit = limit,
                    countUsed = used,
                    countRemaining = limit - used,
                    streakDays = getStreakDays()
                )
                _quotaState.value = status
                return status
            }
        }

        // Vérifier quota en temps
        if (isTimeQuotaEnabled()) {
            val limitMs = getTimeLimitMin().toLong() * 60 * 1000
            val usedMs = prefs.getLong(KEY_TIME_TODAY_MS, 0L)
            if (usedMs >= limitMs) {
                val status = QuotaStatus(
                    isExceeded = true,
                    timeExceeded = true,
                    timeLimitMin = getTimeLimitMin(),
                    timeUsedMs = usedMs,
                    timeRemainingMs = 0,
                    streakDays = getStreakDays()
                )
                _quotaState.value = status
                return status
            }
            val isWarning = usedMs >= (limitMs * 0.8).toLong()
            if (isWarning) {
                val status = QuotaStatus(
                    isWarning = true,
                    timeLimitMin = getTimeLimitMin(),
                    timeUsedMs = usedMs,
                    timeRemainingMs = limitMs - usedMs,
                    streakDays = getStreakDays()
                )
                _quotaState.value = status
                return status
            }
        }

        return QuotaStatus()
    }

    fun recordReelSession(packageName: String, durationMs: Long) {
        prefs.edit {
            // Incrémenter le compteur du jour
            val currentCount = prefs.getInt(KEY_COUNT_TODAY, 0)
            putInt(KEY_COUNT_TODAY, currentCount + 1)

            // Incrémenter le temps du jour
            val currentTime = prefs.getLong(KEY_TIME_TODAY_MS, 0L)
            putLong(KEY_TIME_TODAY_MS, currentTime + durationMs)
        }
        _quotaState.value = getCurrentQuotaStatus()
    }

    // ----------------------------------------------------------------
    // RESET QUOTIDIEN
    // ----------------------------------------------------------------

    fun checkAndResetDailyQuotas() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val lastReset = prefs.getString(KEY_LAST_RESET_DATE, "") ?: ""

        if (today != lastReset) {
            // Mettre à jour le streak avant de reset
            updateStreak(lastReset)

            prefs.edit {
                putInt(KEY_COUNT_TODAY, 0)
                putLong(KEY_TIME_TODAY_MS, 0L)
                putString(KEY_LAST_RESET_DATE, today)
                putBoolean(KEY_QUOTA_MET_TODAY, false)
            }
        }
    }

    private fun updateStreak(lastResetDate: String) {
        try {
            val yesterday = LocalDate.now().minusDays(1)
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            val quotaMetYesterday = prefs.getBoolean(KEY_QUOTA_MET_TODAY, false)

            if (lastResetDate == yesterday && quotaMetYesterday) {
                // Streak continue
                prefs.edit {
                    putInt(KEY_STREAK_DAYS, prefs.getInt(KEY_STREAK_DAYS, 0) + 1)
                }
            } else if (lastResetDate != yesterday) {
                // Streak cassé
                prefs.edit { putInt(KEY_STREAK_DAYS, 0) }
            }
        } catch (_: Exception) {}
    }

    // ----------------------------------------------------------------
    // ACCESSEURS / MUTATEURS DE CONFIGURATION
    // ----------------------------------------------------------------

    fun isBlockingEnabledForApp(packageName: String): Boolean =
        prefs.getBoolean(KEY_APP_PREFIX + packageName, true)

    fun setBlockingEnabledForApp(packageName: String, enabled: Boolean) {
        prefs.edit { putBoolean(KEY_APP_PREFIX + packageName, enabled) }
    }

    fun isCountQuotaEnabled(): Boolean = prefs.getBoolean(KEY_COUNT_ENABLED, false)
    fun setCountQuotaEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_COUNT_ENABLED, enabled) }

    fun getCountLimit(): Int = prefs.getInt(KEY_COUNT_LIMIT, 10)
    fun setCountLimit(limit: Int) = prefs.edit { putInt(KEY_COUNT_LIMIT, limit) }

    fun getCountToday(): Int = prefs.getInt(KEY_COUNT_TODAY, 0)

    fun isTimeQuotaEnabled(): Boolean = prefs.getBoolean(KEY_TIME_ENABLED, true)
    fun setTimeQuotaEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_TIME_ENABLED, enabled) }

    fun getTimeLimitMin(): Int = prefs.getInt(KEY_TIME_LIMIT_MIN, 15)
    fun setTimeLimitMin(min: Int) = prefs.edit { putInt(KEY_TIME_LIMIT_MIN, min) }

    fun getTimeUsedMs(): Long = prefs.getLong(KEY_TIME_TODAY_MS, 0L)

    // Mode Focus
    fun startFocusMode(durationMin: Int) {
        val endTime = System.currentTimeMillis() + durationMin.toLong() * 60 * 1000
        prefs.edit { putLong(KEY_FOCUS_END_TIME, endTime) }
        _quotaState.value = getCurrentQuotaStatus()
    }

    fun stopFocusMode() {
        prefs.edit { putLong(KEY_FOCUS_END_TIME, 0L) }
        _quotaState.value = getCurrentQuotaStatus()
    }

    fun isFocusModeActive(): Boolean {
        val endTime = prefs.getLong(KEY_FOCUS_END_TIME, 0L)
        return endTime > System.currentTimeMillis()
    }

    fun getFocusEndTimeDisplay(): String {
        val endTime = prefs.getLong(KEY_FOCUS_END_TIME, 0L)
        if (endTime == 0L) return ""
        val remaining = (endTime - System.currentTimeMillis()) / 60000
        return "${remaining} min"
    }

    // Plages horaires
    fun isScheduleBlocked(): Boolean {
        if (!prefs.getBoolean(KEY_SCHEDULE_ENABLED, false)) return false
        val rules = getScheduleRules()
        val now = LocalTime.now()
        return rules.any { rule -> rule.isBlocked(now) }
    }

    fun getScheduleRules(): List<ScheduleRule> {
        val json = prefs.getString(KEY_SCHEDULE_RULES, "[]") ?: "[]"
        return ScheduleRule.fromJson(json)
    }

    fun saveScheduleRules(rules: List<ScheduleRule>) {
        prefs.edit { putString(KEY_SCHEDULE_RULES, ScheduleRule.toJson(rules)) }
    }

    fun isScheduleEnabled(): Boolean = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false)
    fun setScheduleEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_SCHEDULE_ENABLED, enabled) }

    private fun getScheduleBlockMessage(): String {
        val rules = getScheduleRules()
        val now = LocalTime.now()
        val activeRule = rules.firstOrNull { it.isBlocked(now) }
        return if (activeRule != null) {
            "Bloqué de ${activeRule.startHour}h à ${activeRule.endHour}h"
        } else ""
    }

    // Streak
    fun getStreakDays(): Int = prefs.getInt(KEY_STREAK_DAYS, 0)

    // PIN
    fun isPinEnabled(): Boolean = prefs.getBoolean(KEY_PIN_ENABLED, false)
    fun setPinEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_PIN_ENABLED, enabled) }
    fun getPinHash(): String = prefs.getString(KEY_PIN_HASH, "") ?: ""
    fun setPin(pin: String) {
        prefs.edit {
            putString(KEY_PIN_HASH, pin.hashCode().toString())
            putBoolean(KEY_PIN_ENABLED, pin.isNotEmpty())
        }
    }
    fun verifyPin(pin: String): Boolean = pin.hashCode().toString() == getPinHash()

    // Messagerie
    fun isInMessagingContext(packageName: String): Boolean = false // Géré dans le service

    // Statut courant
    fun getCurrentQuotaStatus(): QuotaStatus {
        val countEnabled = isCountQuotaEnabled()
        val timeEnabled = isTimeQuotaEnabled()
        val limit = getCountLimit()
        val used = getCountToday()
        val timeLimitMs = getTimeLimitMin().toLong() * 60 * 1000
        val timeUsedMs = getTimeUsedMs()

        return QuotaStatus(
            countLimit = if (countEnabled) limit else -1,
            countUsed = used,
            countRemaining = if (countEnabled) maxOf(0, limit - used) else -1,
            timeLimitMin = if (timeEnabled) getTimeLimitMin() else -1,
            timeUsedMs = timeUsedMs,
            timeRemainingMs = if (timeEnabled) maxOf(0L, timeLimitMs - timeUsedMs) else -1L,
            streakDays = getStreakDays(),
            focusModeActive = isFocusModeActive()
        )
    }
}

data class ScheduleRule(
    val startHour: Int,
    val startMin: Int = 0,
    val endHour: Int,
    val endMin: Int = 0,
    val label: String = ""
) {
    fun isBlocked(time: LocalTime): Boolean {
        val start = LocalTime.of(startHour, startMin)
        val end = LocalTime.of(endHour, endMin)
        return if (start <= end) time >= start && time < end
        else time >= start || time < end // Couvre minuit (ex: 22h-8h)
    }

    companion object {
        fun fromJson(json: String): List<ScheduleRule> {
            return try {
                val list = mutableListOf<ScheduleRule>()
                val arr = org.json.JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(ScheduleRule(
                        startHour = obj.getInt("sh"),
                        startMin = obj.optInt("sm", 0),
                        endHour = obj.getInt("eh"),
                        endMin = obj.optInt("em", 0),
                        label = obj.optString("label", "")
                    ))
                }
                list
            } catch (_: Exception) { emptyList() }
        }

        fun toJson(rules: List<ScheduleRule>): String {
            val arr = org.json.JSONArray()
            rules.forEach { rule ->
                arr.put(org.json.JSONObject().apply {
                    put("sh", rule.startHour)
                    put("sm", rule.startMin)
                    put("eh", rule.endHour)
                    put("em", rule.endMin)
                    put("label", rule.label)
                })
            }
            return arr.toString()
        }
    }
}
