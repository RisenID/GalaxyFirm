package dev.risenid.galaxyfirm.ui.history

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.data.model.ScanSession
import dev.risenid.galaxyfirm.data.model.ScanTrigger
import dev.risenid.galaxyfirm.databinding.ItemHistorySessionBinding
import dev.risenid.galaxyfirm.util.formatTime
import dev.oneuiproject.oneui.widget.Separator

sealed interface HistoryItem {
    val key: String

    data class Header(val day: Long, val label: String) : HistoryItem {
        override val key get() = "h$day"
    }

    data class Row(val session: ScanSession, val lastInGroup: Boolean) : HistoryItem {
        override val key get() = "s${session.id}"
    }
}

class HistoryAdapter(
    private val onClick: (ScanSession) -> Unit
) : ListAdapter<HistoryItem, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is HistoryItem.Header -> TYPE_HEADER
        is HistoryItem.Row -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_HEADER) {
            HeaderHolder(Separator(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        } else {
            RowHolder(ItemHistorySessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HistoryItem.Header -> (holder as HeaderHolder).separator.text = item.label
            is HistoryItem.Row -> (holder as RowHolder).bind(item)
        }
    }

    class HeaderHolder(val separator: Separator) : RecyclerView.ViewHolder(separator)

    inner class RowHolder(private val binding: ItemHistorySessionBinding) : RecyclerView.ViewHolder(binding.root) {
        var lastInGroup = false
            private set

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) (getItem(position) as? HistoryItem.Row)?.let { onClick(it.session) }
            }
        }

        fun bind(item: HistoryItem.Row) = with(binding) {
            val session = item.session
            val context = root.context
            val kind = StatusKind.of(session)
            lastInGroup = item.lastInGroup
            statusIcon.bindStatus(kind)
            title.text = context.resources.getQuantityString(R.plurals.history_devices_scanned, session.deviceCount, session.deviceCount)
            summary.text = sessionSummary(context, session)
            chipModel.text = session.firstModel
            chipModel.isVisible = session.firstModel != null
            chipCsc.text = session.firstCsc
            chipCsc.isVisible = session.firstCsc != null
            val more = session.deviceCount - 1
            chipMore.text = context.getString(R.string.history_more_devices, more)
            chipMore.isVisible = more > 0
            badge.bindBadge(
                kind,
                context.getString(
                    when (kind) {
                        StatusKind.NEW -> R.string.history_badge_new
                        StatusKind.FAILED -> R.string.history_badge_failed
                        StatusKind.OK -> R.string.history_badge_completed
                    }
                )
            )
            time.text = formatTime(context, session.startedAt)
            trigger.setText(if (session.trigger == ScanTrigger.AUTO) R.string.history_trigger_auto else R.string.history_trigger_manual)
        }
    }

    private object Diff : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem) = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem) = oldItem == newItem
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ROW = 1
    }
}

private fun sessionSummary(context: Context, session: ScanSession): CharSequence {
    val res = context.resources
    val separator = context.getString(R.string.history_separator)
    return SpannableStringBuilder().apply {
        fun part(text: String, color: Int? = null) {
            if (isNotEmpty()) append(separator)
            val start = length
            append(text)
            color?.let { setSpan(ForegroundColorSpan(ContextCompat.getColor(context, it)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
        if (session.newCount > 0) part(res.getQuantityString(R.plurals.history_new_updates, session.newCount, session.newCount), R.color.status_new)
        if (session.failedCount > 0) part(res.getQuantityString(R.plurals.history_failed, session.failedCount, session.failedCount), R.color.status_failed)
        if (session.upToDateCount > 0 || isEmpty()) part(res.getQuantityString(R.plurals.history_up_to_date, session.upToDateCount, session.upToDateCount))
    }
}
