package dev.risenid.galaxyfirm.ui.devices

import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.text.InputFilter
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.data.model.AlertLevel
import dev.risenid.galaxyfirm.data.model.Device
import dev.risenid.galaxyfirm.data.model.DeviceStatus
import dev.risenid.galaxyfirm.data.model.DeviceType
import dev.risenid.galaxyfirm.data.model.alerts
import dev.risenid.galaxyfirm.data.net.BuildVersion
import dev.risenid.galaxyfirm.databinding.ActivityDeviceEditBinding
import dev.risenid.galaxyfirm.util.ThisDevice
import dev.risenid.galaxyfirm.util.userDeviceName
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.ktx.semToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceEditBinding
    private val repository by lazy { ServiceLocator.repository(this) }
    private var editing: Device? = null
    private var type = DeviceType.PHONE
    private var typePicked = false
    private var thisDevice = false
    private var alertLevel = AlertLevel.ALL
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val id = intent.getLongExtra(EXTRA_DEVICE_ID, 0L)
        binding.toolbarLayout.setTitle(getString(if (id > 0L) R.string.device_edit_title_edit else R.string.device_edit_title_add))
        savedInstanceState?.let {
            type = DeviceType.entries[it.getInt(KEY_TYPE)]
            typePicked = it.getBoolean(KEY_TYPE_PICKED)
            thisDevice = it.getBoolean(KEY_THIS_DEVICE)
            alertLevel = AlertLevel.entries[it.getInt(KEY_ALERTS)]
        }
        setupInputs()
        if (id <= 0L) lifecycleScope.launch { withContext(Dispatchers.IO) { ThisDevice.current }?.let(::setupThisPhone) }
        updateType()
        updateThisDevice()
        updateAlerts()
        binding.bottomActions.setOnItemSelectedListener {
            onAction(it.itemId)
            false
        }
        binding.bottomActions.setOnItemReselectedListener { onAction(it.itemId) }
        if (id > 0L) {
            lifecycleScope.launch {
                val device = repository.getDevice(id) ?: return@launch finish()
                editing = device
                if (savedInstanceState == null) fill(device)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TYPE, type.ordinal)
        outState.putBoolean(KEY_TYPE_PICKED, typePicked)
        outState.putBoolean(KEY_THIS_DEVICE, thisDevice)
        outState.putInt(KEY_ALERTS, alertLevel.ordinal)
    }

    private fun setupInputs() {
        binding.modelInput.filters = arrayOf(InputFilter.AllCaps(), allowed("-"), InputFilter.LengthFilter(20))
        binding.cscInput.filters = arrayOf(InputFilter.AllCaps(), allowed(""), InputFilter.LengthFilter(3))
        binding.installedInput.filters = arrayOf(InputFilter.AllCaps(), allowed(""), InputFilter.LengthFilter(20))
        binding.modelInput.doAfterTextChanged { text ->
            showError(binding.modelError, null)
            if (!typePicked) {
                type = DeviceType.guess(text.toString())
                updateType()
            }
            checkThisDevice()
        }
        binding.cscInput.doAfterTextChanged { text ->
            showError(binding.cscError, null)
            val region = cscRegion(text.toString())
            binding.cscRegion.text = region
            binding.cscRegion.isVisible = region != null
            checkThisDevice()
        }
        binding.installedInput.doAfterTextChanged { binding.installedError.isVisible = false }
        binding.installedInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) save() }
        }
        binding.cscPick.setOnClickListener { pickCsc() }
        binding.typeItem.setOnClickListener { pickType() }
        binding.alertsItem.setOnClickListener {
            pickAlertLevel(alertLevel) {
                alertLevel = it
                updateAlerts()
            }
        }
    }

    private fun setupThisPhone(phone: ThisDevice) {
        binding.thisPhoneCard.isVisible = true
        binding.thisPhoneItem.summary = getString(R.string.device_detail_subtitle, phone.model, phone.csc)
        binding.thisPhoneItem.setOnClickListener { fillThisPhone(phone) }
    }

    private fun fillThisPhone(phone: ThisDevice) {
        if (binding.nameInput.text.isNullOrBlank()) {
            binding.nameInput.setText(userDeviceName() ?: getString(R.string.devices_this_phone_name))
        }
        binding.modelInput.setText(phone.model)
        binding.cscInput.setText(phone.csc)
        thisDevice = true
        binding.installedInput.setText(phone.installedPda)
        updateThisDevice()
    }

    private fun checkThisDevice() {
        if (!thisDevice) return
        val model = binding.modelInput.text.toString()
        val csc = binding.cscInput.text.toString()
        if (model.isEmpty() || csc.isEmpty() || ThisDevice.current?.matches(model, csc) == true) return
        thisDevice = false
        binding.installedInput.text = null
        updateThisDevice()
    }

    private fun updateThisDevice() {
        binding.installedInput.isEnabled = !thisDevice
        binding.installedNote.isVisible = thisDevice
    }

    private fun updateAlerts() {
        binding.alertsItem.summary = getString(alertLevel.label)
    }

    private fun allowed(extra: String) = InputFilter { source, start, end, _, _, _ ->
        val filtered = source.subSequence(start, end).filter { it.isLetterOrDigit() && it.code < 128 || it in extra }
        if (filtered.length == end - start) null else filtered
    }

    private fun fill(device: Device) {
        type = device.type
        typePicked = device.type != DeviceType.guess(device.model)
        binding.nameInput.setText(device.name.takeIf { it != device.model })
        binding.modelInput.setText(device.model)
        binding.cscInput.setText(device.csc)
        binding.installedInput.setText(device.installedFirmware)
        thisDevice = device.isThisDevice
        alertLevel = device.alerts
        updateType()
        updateThisDevice()
        updateAlerts()
    }

    private fun updateType() {
        val label = getString(type.label)
        binding.typeItem.summary = if (typePicked || binding.modelInput.text.isNullOrBlank()) {
            label
        } else {
            getString(R.string.device_edit_type_auto, label)
        }
        binding.typeItem.icon = ContextCompat.getDrawable(this, type.illustration)
    }

    private fun onAction(itemId: Int) {
        when (itemId) {
            R.id.menu_device_edit_cancel -> finish()
            R.id.menu_device_edit_save -> save()
        }
    }

    private fun pickCsc() {
        val codes = resources.getStringArray(R.array.device_csc_codes)
        val regions = resources.getStringArray(R.array.device_csc_regions)
        val items = codes.indices.map { getString(R.string.device_edit_csc_item, regions[it], codes[it]) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.device_edit_csc_pick)
            .setSingleChoiceItems(items, codes.indexOf(binding.cscInput.text.toString())) { dialog, which ->
                binding.cscInput.setText(codes[which])
                binding.cscInput.setSelection(codes[which].length)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.devices_cancel, null)
            .show()
    }

    private fun pickType() {
        val labels = DeviceType.entries.map { getString(it.label) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.device_edit_type)
            .setSingleChoiceItems(labels, type.ordinal) { dialog, which ->
                type = DeviceType.entries[which]
                typePicked = true
                updateType()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.devices_cancel, null)
            .show()
    }

    private fun showError(view: TextView, @StringRes message: Int?) {
        view.isVisible = message != null
        message?.let(view::setText)
    }

    private fun save() {
        if (saving) return
        val name = binding.nameInput.text.toString().trim()
        val model = binding.modelInput.text.toString().trim().uppercase()
        val csc = binding.cscInput.text.toString().trim().uppercase()
        val installed = binding.installedInput.text.toString().trim().uppercase().takeIf { it.isNotEmpty() }
        val modelError = when {
            model.isEmpty() -> R.string.device_edit_error_model_empty
            !MODEL_PATTERN.matches(model) -> R.string.device_edit_error_model_format
            else -> null
        }
        val cscError = when {
            csc.isEmpty() -> R.string.device_edit_error_csc_empty
            csc.length != 3 -> R.string.device_edit_error_csc_format
            else -> null
        }
        val installedError = installed?.takeUnless { thisDevice || BuildVersion.isPlausiblePda(it, model) }?.let { installedError(model) }
        showError(binding.modelError, modelError)
        showError(binding.cscError, cscError)
        binding.installedError.isVisible = installedError != null
        binding.installedError.text = installedError
        when {
            modelError != null -> return focus(binding.modelInput)
            cscError != null -> return focus(binding.cscInput)
            installedError != null -> return focus(binding.installedInput)
        }
        saving = true
        val progress = ProgressDialog(this).apply {
            setProgressStyle(ProgressDialog.ProgressStyle.CIRCLE)
            isIndeterminate = true
            setMessage(getString(R.string.device_edit_verifying))
            setCancelable(false)
            show()
        }
        lifecycleScope.launch {
            try {
                val current = editing
                if (current == null) add(name, model, csc, installed) else update(current, name, model, csc, installed)
            } finally {
                progress.dismiss()
                saving = false
            }
        }
    }

    private fun installedError(model: String): String =
        BuildVersion.pdaPrefix(model)
            ?.let { getString(R.string.device_edit_error_installed_format, it) }
            ?: getString(R.string.device_edit_error_installed_generic)

    private fun focus(input: EditText) {
        input.requestFocus()
        input.setSelection(input.length())
    }

    private suspend fun add(name: String, model: String, csc: String, installed: String?) {
        val id = repository.addDevice(name, model, csc, type, installed, thisDevice, alertLevel).getOrElse { error ->
            if (error is IllegalStateException) {
                showError(binding.modelError, R.string.device_edit_error_duplicate)
                focus(binding.modelInput)
            } else {
                semToast(getString(R.string.device_edit_error_generic))
            }
            return
        }
        val device = repository.scanOne(id)
        if (device?.status == DeviceStatus.FAILED) verifyFailed(device, removeOnFix = true) else finish()
    }

    private suspend fun update(current: Device, name: String, model: String, csc: String, installed: String?) {
        val retarget = current.model != model || current.csc != csc
        val updated = if (retarget) {
            Device(id = current.id, name = name, model = model, csc = csc, type = type, createdAt = current.createdAt)
        } else {
            current
        }.copy(name = name, type = type, installedFirmware = installed, isThisDevice = thisDevice, alertLevel = alertLevel.name)
        try {
            repository.updateDevice(updated)
        } catch (_: SQLiteConstraintException) {
            showError(binding.modelError, R.string.device_edit_error_duplicate)
            return focus(binding.modelInput)
        }
        if (!retarget) return finish()
        editing = updated
        val device = repository.scanOne(current.id)
        if (device?.status == DeviceStatus.FAILED) verifyFailed(device, removeOnFix = false) else finish()
    }

    private fun verifyFailed(device: Device, removeOnFix: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(R.string.device_edit_verify_failed_title)
            .setMessage(getString(R.string.device_edit_verify_failed_message, device.model, device.csc))
            .setCancelable(false)
            .setNegativeButton(R.string.device_edit_verify_fix) { _, _ ->
                if (removeOnFix) lifecycleScope.launch { repository.deleteDevice(device) }
                focus(binding.modelInput)
            }
            .setPositiveButton(R.string.device_edit_verify_keep) { _, _ -> finish() }
            .show()
    }

    private companion object {
        const val KEY_TYPE = "type"
        const val KEY_TYPE_PICKED = "type_picked"
        const val KEY_THIS_DEVICE = "this_device"
        const val KEY_ALERTS = "alerts"
        val MODEL_PATTERN = Regex("^[A-Z]{2,3}-[A-Z0-9]{3,}$")
    }
}
