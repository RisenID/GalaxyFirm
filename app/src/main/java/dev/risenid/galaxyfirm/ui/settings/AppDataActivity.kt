package dev.risenid.galaxyfirm.ui.settings

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.databinding.ActivityAppDataBinding
import dev.oneuiproject.oneui.ktx.seslSetFillHorizontalPaddingEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDataActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppDataBinding
    private val settings by lazy { ServiceLocator.settings(this) }
    private val repository by lazy { ServiceLocator.repository(this) }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            exportToUri(uri)
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDataBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scrollView.apply {
            seslSetFillHorizontalPaddingEnabled(true)
            seslSetGoToTopEnabled(true)
        }

        binding.rowDeleteAll.getTitleView().setTextColor(
            ContextCompat.getColor(this, R.color.status_failed)
        )

        binding.rowExport.setOnClickListener {
            exportLauncher.launch("galaxyfirm-backup.json")
        }
        binding.rowImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }
        binding.rowHistoryLimit.setOnClickListener {
            showHistoryLimitDialog()
        }
        binding.rowClearHistory.setOnClickListener {
            showClearHistoryDialog()
        }
        binding.rowDeleteAll.setOnClickListener {
            showDeleteAllDialog()
        }

        updateHistoryLimitSummary()
    }

    override fun onResume() {
        super.onResume()
        updateHistoryLimitSummary()
    }

    private fun updateHistoryLimitSummary() {
        val limit = settings.historyLimit
        binding.rowHistoryLimit.summary = resources.getQuantityString(
            R.plurals.settings_history_limit_sessions,
            limit,
            limit
        )
    }

    private fun exportToUri(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = repository.exportJson()
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Output stream unavailable")
                }
            }
            val msgRes = if (result.isSuccess) {
                R.string.settings_export_success
            } else {
                R.string.settings_export_failed
            }
            Toast.makeText(this@AppDataActivity, msgRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFromUri(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: throw IllegalArgumentException()
                    repository.importJson(json)
                }
            }
            result.onSuccess { count ->
                val msg = resources.getQuantityString(
                    R.plurals.settings_imported_devices,
                    count,
                    count
                )
                Toast.makeText(this@AppDataActivity, msg, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(
                    this@AppDataActivity,
                    R.string.settings_import_invalid,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showHistoryLimitDialog() {
        val values = resources.getIntArray(R.array.settings_history_limit_values)
        val labels = Array(values.size) { index ->
            val v = values[index]
            resources.getQuantityString(R.plurals.settings_history_limit_sessions, v, v)
        }
        val currentIndex = values.indexOf(settings.historyLimit).let { if (it >= 0) it else 2 }

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_history_limit_title)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                settings.historyLimit = values[which]
                updateHistoryLimitSummary()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_clear_history_title)
            .setMessage(R.string.settings_clear_history_confirm)
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_clear_action) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        repository.clearHistory()
                    }
                    Toast.makeText(
                        this@AppDataActivity,
                        R.string.settings_history_cleared,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    private fun showDeleteAllDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_delete_all_title)
            .setMessage(R.string.settings_delete_all_confirm)
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_delete_action) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        repository.clearAll()
                    }
                    Toast.makeText(
                        this@AppDataActivity,
                        R.string.settings_all_data_deleted,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(this, R.color.status_failed)
        )
    }
}
