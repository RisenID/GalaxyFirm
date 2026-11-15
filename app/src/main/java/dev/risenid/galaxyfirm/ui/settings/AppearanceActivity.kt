package dev.risenid.galaxyfirm.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.databinding.ActivityAppearanceBinding
import dev.oneuiproject.oneui.ktx.seslSetFillHorizontalPaddingEnabled

class AppearanceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppearanceBinding
    private val settings by lazy { ServiceLocator.settings(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppearanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scrollView.apply {
            seslSetFillHorizontalPaddingEnabled(true)
            seslSetGoToTopEnabled(true)
        }

        binding.themeGroup.check(themeToRadioId(settings.theme))
        binding.themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = radioIdToTheme(checkedId)
            if (settings.theme != newTheme) {
                settings.theme = newTheme
                AppCompatDelegate.setDefaultNightMode(settings.nightMode())
            }
        }
    }

    private fun themeToRadioId(theme: String): Int = when (theme) {
        "light" -> R.id.radio_theme_light
        "dark" -> R.id.radio_theme_dark
        else -> R.id.radio_theme_system
    }

    private fun radioIdToTheme(id: Int): String = when (id) {
        R.id.radio_theme_light -> "light"
        R.id.radio_theme_dark -> "dark"
        else -> "system"
    }
}
