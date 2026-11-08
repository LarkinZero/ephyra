package dev.jdtech.jellyfin.presentation.player

import android.app.Dialog
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.jdtech.jellyfin.BasePlayerActivity
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.player.local.presentation.label

/** CE (#298): the sleep timer choices, as [SpeedSelectionDialogFragment] lists the speeds. */
class SleepTimerDialogFragment : DialogFragment() {
    private val viewModel: PlayerViewModel
        get() = (requireActivity() as BasePlayerActivity).viewModel

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val choices = viewModel.sleepTimerChoices
        val current = viewModel.sleepTimerState.value.option
        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(getString(R.string.sleep_timer))
            .setSingleChoiceItems(
                choices.map { it.label(requireContext()) }.toTypedArray(),
                choices.indexOf(current),
            ) { dialog, which ->
                viewModel.setSleepTimer(choices[which])
                dialog.dismiss()
            }
            .create()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Fix for hiding the system bars on API < 30
        activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
