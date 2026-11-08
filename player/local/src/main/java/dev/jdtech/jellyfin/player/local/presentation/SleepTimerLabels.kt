package dev.jdtech.jellyfin.player.local.presentation

import android.content.Context
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.domain.SleepTimerOption

/** CE (#298): the name of a sleep timer choice, as the phone and TV dialogs list it. */
fun SleepTimerOption.label(context: Context): String =
    when (this) {
        SleepTimerOption.Off -> context.getString(R.string.sleep_timer_off)
        is SleepTimerOption.Duration ->
            context.resources.getQuantityString(R.plurals.sleep_timer_minutes, minutes, minutes)
        SleepTimerOption.EndOfItem -> context.getString(R.string.sleep_timer_end_of_episode)
    }
