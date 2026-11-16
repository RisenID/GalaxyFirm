package dev.risenid.galaxyfirm.ui.settings

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.oneuiproject.oneui.ktx.semToast
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.databinding.ActivityEasterEggBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EasterEgg(private val activity: AppCompatActivity) {
    private var taps = 0
    private var lastTap = 0L

    fun attach(target: View) {
        target.isClickable = true
        target.setOnClickListener { onTap(it) }
    }

    private fun onTap(view: View) {
        val now = System.currentTimeMillis()
        taps = if (now - lastTap > TAP_WINDOW_MS) 1 else taps + 1
        lastTap = now
        val left = TAPS_NEEDED - taps
        when {
            left == 0 -> {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                activity.startActivity(Intent(activity, EasterEggActivity::class.java))
            }
            left in 1..3 -> activity.semToast(activity.resources.getQuantityString(R.plurals.settings_egg_taps_left, left, left))
        }
    }

    private companion object {
        const val TAPS_NEEDED = 10
        const val TAP_WINDOW_MS = 1500L
    }
}

class EasterEggActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEasterEggBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEasterEggBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.eggBox.text = getString(R.string.settings_egg_port)
        binding.eggLog.setLines(VISIBLE_LINES)
        binding.eggReboot.setOnClickListener { finish() }
        lifecycleScope.launch { flash() }
    }

    private suspend fun flash() {
        val lines = resources.getStringArray(R.array.settings_egg_log)
        val log = ArrayDeque<String>()
        lines.forEachIndexed { index, line ->
            delay(if (index == 0) 500 else 260L + (index % 3) * 140L)
            log.addLast(line)
            if (log.size > VISIBLE_LINES) log.removeFirst()
            binding.eggLog.text = log.joinToString("\n")
            binding.eggProgress.progress = ((index + 1) * 100) / lines.size
        }
        delay(300)
        binding.eggBox.text = getString(R.string.settings_egg_pass)
        binding.eggBox.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        (binding.eggBox.background.mutate() as? GradientDrawable)?.setColor(ContextCompat.getColor(this, R.color.status_new))
        binding.eggBox.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
        binding.eggFooter.text = getString(R.string.settings_egg_footer)
        binding.eggFooter.visibility = View.VISIBLE
        binding.eggReboot.visibility = View.VISIBLE
    }

    private companion object {
        const val VISIBLE_LINES = 12
    }
}
