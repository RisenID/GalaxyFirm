package dev.risenid.galaxyfirm.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.data.model.HistoryFilter
import dev.risenid.galaxyfirm.data.model.ScanSession
import dev.risenid.galaxyfirm.databinding.FragmentHistoryBinding
import dev.risenid.galaxyfirm.ui.TabScreen
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoryFragment : Fragment(), TabScreen {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val repository by lazy { ServiceLocator.repository(requireContext()) }
    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private var hasSessions = false

    override val menuRes get() = if (hasSessions) R.menu.menu_history else 0

    override fun onMenuItem(itemId: Int): Boolean {
        if (itemId != R.id.menu_history_clear) return false
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.history_clear_title)
            .setMessage(R.string.history_clear_message)
            .setNegativeButton(R.string.history_cancel, null)
            .setPositiveButton(R.string.history_clear_confirm) { _, _ ->
                lifecycleScope.launch { repository.clearHistory() }
            }
            .show()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { filter.value = HistoryFilter.entries[it.getInt(KEY_FILTER)] }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentHistoryBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = HistoryAdapter { openSession(it) }
        binding.sessionList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
            setupRoundedList { it is HistoryAdapter.RowHolder && !it.lastInGroup }
        }
        binding.filterTabs.apply {
            getTabAt(filter.value.ordinal)?.select()
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    filter.value = HistoryFilter.entries[tab.position]
                }
                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = binding.sessionList.smoothScrollToPosition(0)
            })
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(repository.sessions, filter, ::Pair).collect { (sessions, selected) ->
                    render(adapter, sessions, selected)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_FILTER, filter.value.ordinal)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(adapter: HistoryAdapter, sessions: List<ScanSession>, selected: HistoryFilter) {
        val counts = repository.historyCounts(sessions)
        listOf(
            getString(R.string.history_filter_all, counts.all),
            getString(R.string.history_filter_new, counts.new),
            getString(R.string.history_filter_failed, counts.failed)
        ).forEachIndexed { index, label -> binding.filterTabs.getTabAt(index)?.text = label }
        val visible = when (selected) {
            HistoryFilter.ALL -> sessions
            HistoryFilter.NEW -> sessions.filter { it.newCount > 0 }
            HistoryFilter.FAILED -> sessions.filter { it.failedCount > 0 }
        }
        adapter.submitList(group(visible))
        binding.sessionList.isVisible = visible.isNotEmpty()
        binding.emptyView.isVisible = visible.isEmpty()
        val (title, hint) = when {
            sessions.isEmpty() || selected == HistoryFilter.ALL -> R.string.history_empty_title to R.string.history_empty_hint
            selected == HistoryFilter.NEW -> R.string.history_empty_new_title to R.string.history_empty_new_hint
            else -> R.string.history_empty_failed_title to R.string.history_empty_failed_hint
        }
        binding.emptyTitle.setText(title)
        binding.emptyHint.setText(hint)
        if (hasSessions != sessions.isNotEmpty()) {
            hasSessions = sessions.isNotEmpty()
            if (!isHidden) requireActivity().invalidateOptionsMenu()
        }
    }

    private fun group(sessions: List<ScanSession>): List<HistoryItem> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        return sessions
            .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .flatMap { (day, group) ->
                val label = when (day) {
                    today -> getString(R.string.history_today)
                    today.minusDays(1) -> getString(R.string.history_yesterday)
                    else -> day.format(formatter)
                }
                listOf(HistoryItem.Header(day.toEpochDay(), label)) +
                    group.mapIndexed { index, session -> HistoryItem.Row(session, index == group.lastIndex) }
            }
    }

    private fun openSession(session: ScanSession) {
        startActivity(SessionDetailActivity.intent(requireContext(), session))
    }

    private companion object {
        const val KEY_FILTER = "history_filter"
    }
}
