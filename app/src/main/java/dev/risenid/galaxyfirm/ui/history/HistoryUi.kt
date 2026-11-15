package dev.risenid.galaxyfirm.ui.history

import android.content.Context
import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.data.model.DeviceStatus
import dev.risenid.galaxyfirm.data.model.ScanSession
import dev.oneuiproject.oneui.ktx.dpToPx
import dev.oneuiproject.oneui.ktx.getThemeAttributeValue
import dev.oneuiproject.oneui.recyclerview.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.utils.ItemDecorRule
import dev.oneuiproject.oneui.utils.SemItemDecoration
import dev.oneuiproject.oneui.widget.Separator
import dev.oneuiproject.oneui.R as iconsR

internal enum class StatusKind(
    @DrawableRes val icon: Int,
    @ColorRes val color: Int,
    @ColorRes val container: Int,
    @DrawableRes val badge: Int
) {
    NEW(iconsR.drawable.ic_oui_download_circle_outline, R.color.status_new, R.color.status_new_container, R.drawable.bg_badge_new),
    FAILED(iconsR.drawable.ic_oui_error, R.color.status_failed, R.color.status_failed_container, R.drawable.bg_badge_failed),
    OK(iconsR.drawable.ic_oui_checkbox_checked_outline, R.color.status_neutral, R.color.status_neutral_container, R.drawable.bg_badge_neutral);

    companion object {
        fun of(session: ScanSession) = when {
            session.newCount > 0 -> NEW
            session.failedCount > 0 -> FAILED
            else -> OK
        }

        fun of(status: DeviceStatus) = when (status) {
            DeviceStatus.NEW_UPDATE -> NEW
            DeviceStatus.FAILED -> FAILED
            else -> OK
        }
    }
}

internal fun ImageView.bindStatus(kind: StatusKind) {
    setImageResource(kind.icon)
    imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, kind.color))
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, kind.container))
}

internal fun TextView.bindBadge(kind: StatusKind, text: CharSequence) {
    this.text = text
    setBackgroundResource(kind.badge)
    setTextColor(ContextCompat.getColor(context, kind.color))
}

internal fun RecyclerView.setupRoundedList(isDivided: (RecyclerView.ViewHolder) -> Boolean) {
    addItemDecoration(
        SemItemDecoration(
            context,
            dividerRule = ItemDecorRule.SELECTED(isDivided),
            subHeaderRule = ItemDecorRule.SELECTED { it.itemView is Separator }
        ).apply { setDividerInsetStart(context.listTextInset()) }
    )
    enableCoreSeslFeatures(fastScrollerEnabled = false)
}

private fun Context.listTextInset(): Int {
    val start = getThemeAttributeValue(androidx.appcompat.R.attr.listPreferredItemPaddingStart)
        ?.getDimension(resources.displayMetrics)?.toInt() ?: 0
    return start + 56.dpToPx(resources)
}
