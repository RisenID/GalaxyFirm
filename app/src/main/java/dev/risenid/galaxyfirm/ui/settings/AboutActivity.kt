package dev.risenid.galaxyfirm.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.databinding.ActivityAboutBinding
import dev.oneuiproject.oneui.layout.AppInfoLayout
import dev.oneuiproject.oneui.design.R as oneuiR

class AboutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val upperLayout = binding.appInfoLayout.findViewById<LinearLayout>(oneuiR.id.app_info_upper_layout)
        val nameView = binding.appInfoLayout.findViewById<View>(oneuiR.id.app_info_name)
        if (upperLayout != null && nameView != null) {
            val size = (72 * resources.displayMetrics.density).toInt()
            val bottomMargin = (12 * resources.displayMetrics.density).toInt()
            val iconView = ImageView(this).apply {
                setImageResource(R.mipmap.ic_launcher)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    this.bottomMargin = bottomMargin
                }
            }
            val index = upperLayout.indexOfChild(nameView).coerceAtLeast(0)
            upperLayout.addView(iconView, index)
        }

        binding.appInfoLayout.updateStatus = AppInfoLayout.Status.Unset
        binding.appInfoLayout.addOptionalText(getString(R.string.settings_about_disclaimer))

        binding.btnLicenses.setOnClickListener {
            showLicensesDialog()
        }
    }

    private fun showLicensesDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_open_source_licenses)
            .setMessage(R.string.settings_open_source_licenses_body)
            .setPositiveButton(R.string.settings_ok, null)
            .show()
    }
}
