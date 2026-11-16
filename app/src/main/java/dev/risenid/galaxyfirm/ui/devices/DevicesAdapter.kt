package dev.risenid.galaxyfirm.ui.devices

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.data.model.Device
import dev.risenid.galaxyfirm.data.model.DeviceStatus
import dev.risenid.galaxyfirm.databinding.ItemDeviceBinding
import dev.risenid.galaxyfirm.util.formatStamp

data class DeviceRow(val device: Device, val checking: Boolean)

class DevicesAdapter(
    private val onClick: (Device) -> Unit,
    private val onLongClick: (View, Device) -> Unit
) : ListAdapter<DeviceRow, DevicesAdapter.Holder>(Diff) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).device.id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val illustration = binding.illustration.layoutParams as ViewGroup.MarginLayoutParams
        val text = (binding.name.parent as View).layoutParams as ViewGroup.MarginLayoutParams
        binding.divider.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = binding.row.paddingStart + illustration.width + text.marginStart
        }
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position), position == 0)

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_DIVIDER }) {
            holder.binding.divider.isVisible = position != 0
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onCurrentListChanged(previousList: MutableList<DeviceRow>, currentList: MutableList<DeviceRow>) {
        if (currentList.isNotEmpty()) notifyItemRangeChanged(0, currentList.size, PAYLOAD_DIVIDER)
    }

    inner class Holder(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        private val secondary: ColorStateList = binding.firmware.textColors

        init {
            binding.row.setOnClickListener { current()?.let(onClick) }
            binding.row.setOnLongClickListener { view -> current()?.let { onLongClick(view, it) } != null }
        }

        private fun current(): Device? =
            bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { getItem(it).device }

        fun bind(row: DeviceRow, first: Boolean) {
            val device = row.device
            val context = binding.root.context
            binding.divider.isVisible = !first
            binding.illustration.setImageResource(device.type.illustration)
            binding.name.text = device.name
            binding.firmware.text = device.firmware ?: context.getString(R.string.devices_not_checked_yet)
            if (device.status == DeviceStatus.NEW_UPDATE) {
                binding.firmware.setTextColor(ContextCompat.getColor(context, R.color.status_new))
            } else {
                binding.firmware.setTextColor(secondary)
            }
            binding.model.text = device.model
            binding.csc.text = device.csc
            binding.badge.bindBadge(device)
            binding.badge.isInvisible = row.checking
            binding.progress.isVisible = row.checking
            val found = device.updateFoundAt?.takeIf { device.status == DeviceStatus.NEW_UPDATE }
            val spotted = device.upcomingFoundAt?.takeIf { device.showsComingSoon }
            binding.stamp.text = when {
                row.checking -> context.getString(R.string.devices_checking)
                found != null -> context.getString(R.string.devices_found_at, formatStamp(context, found))
                spotted != null -> context.getString(R.string.devices_spotted_at, formatStamp(context, spotted))
                device.lastCheckedAt != null -> context.getString(R.string.devices_checked_at, formatStamp(context, device.lastCheckedAt))
                else -> null
            }
            binding.stamp.isVisible = binding.stamp.text.isNotEmpty()
        }
    }

    private object Diff : DiffUtil.ItemCallback<DeviceRow>() {
        override fun areItemsTheSame(oldItem: DeviceRow, newItem: DeviceRow) = oldItem.device.id == newItem.device.id
        override fun areContentsTheSame(oldItem: DeviceRow, newItem: DeviceRow) = oldItem == newItem
    }

    private companion object {
        const val PAYLOAD_DIVIDER = "divider"
    }
}
