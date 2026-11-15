package dev.risenid.galaxyfirm.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.data.model.DeviceStatus
import dev.risenid.galaxyfirm.data.model.ScanResult
import dev.risenid.galaxyfirm.data.net.UpdateType
import dev.risenid.galaxyfirm.databinding.ItemSessionResultBinding
import dev.risenid.galaxyfirm.databinding.ItemSessionSummaryBinding
import dev.oneuiproject.oneui.widget.Separator

sealed interface SessionItem {
    val key: String

    data class Summary(val new: Int, val upToDate: Int, val failed: Int) : SessionItem {
        override val key get() = "summary"
    }

    data class Header(val label: String) : SessionItem {
        override val key get() = "header"
    }

    data class Result(val result: ScanResult, val last: Boolean) : SessionItem {
        override val key get() = "r${result.id}"
    }
}

class SessionResultsAdapter : ListAdapter<SessionItem, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is SessionItem.Summary -> TYPE_SUMMARY
        is SessionItem.Header -> TYPE_HEADER
        is SessionItem.Result -> TYPE_RESULT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SUMMARY -> SummaryHolder(ItemSessionSummaryBinding.inflate(inflater, parent, false))
            TYPE_HEADER -> HeaderHolder(Separator(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            else -> ResultHolder(ItemSessionResultBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SessionItem.Summary -> (holder as SummaryHolder).bind(item)
            is SessionItem.Header -> (holder as HeaderHolder).separator.text = item.label
            is SessionItem.Result -> (holder as ResultHolder).bind(item)
        }
    }

    class SummaryHolder(private val binding: ItemSessionSummaryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SessionItem.Summary) = with(binding) {
            newCount.text = item.new.toString()
            upToDateCount.text = item.upToDate.toString()
            failedCount.text = item.failed.toString()
        }
    }

    class HeaderHolder(val separator: Separator) : RecyclerView.ViewHolder(separator)

    class ResultHolder(private val binding: ItemSessionResultBinding) : RecyclerView.ViewHolder(binding.root) {
        var last = false
            private set

        fun bind(item: SessionItem.Result) = with(binding) {
            val result = item.result
            val context = root.context
            val kind = StatusKind.of(result)
            last = item.last
            statusIcon.bindStatus(kind)
            name.text = result.deviceName
            chipModel.text = result.model
            chipCsc.text = result.csc
            firmware.text = when {
                result.firmware == null -> context.getString(R.string.session_firmware_unknown)
                result.status == DeviceStatus.NEW_UPDATE && result.previousFirmware != null ->
                    context.getString(R.string.session_firmware_change, result.previousFirmware, result.firmware)
                else -> result.firmware
            }
            error.text = result.error
            error.isVisible = result.status == DeviceStatus.FAILED && !result.error.isNullOrBlank()
            badge.bindBadge(
                kind,
                context.getString(
                    when (kind) {
                        StatusKind.MAJOR -> R.string.session_status_major
                        StatusKind.NEW -> if (result.updateType == UpdateType.SECURITY) {
                            R.string.session_status_security
                        } else {
                            R.string.session_status_new
                        }
                        StatusKind.FAILED -> R.string.session_status_failed
                        StatusKind.OK -> R.string.session_status_up_to_date
                    }
                )
            )
        }
    }

    private object Diff : DiffUtil.ItemCallback<SessionItem>() {
        override fun areItemsTheSame(oldItem: SessionItem, newItem: SessionItem) = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: SessionItem, newItem: SessionItem) = oldItem == newItem
    }

    private companion object {
        const val TYPE_SUMMARY = 0
        const val TYPE_HEADER = 1
        const val TYPE_RESULT = 2
    }
}
