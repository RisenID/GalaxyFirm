package dev.risenid.galaxyfirm.ui.history

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.data.model.ScanResult
import dev.risenid.galaxyfirm.data.model.ScanSession
import dev.risenid.galaxyfirm.data.model.ScanTrigger
import dev.risenid.galaxyfirm.databinding.ActivitySessionDetailBinding
import dev.risenid.galaxyfirm.util.formatStamp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SessionDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySessionDetailBinding
    private val repository by lazy { ServiceLocator.repository(this) }
    private val adapter = SessionResultsAdapter()
    private var session: ScanSession? = null
    private var results: List<ScanResult> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val id = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        binding.resultList.apply {
            layoutManager = LinearLayoutManager(this@SessionDetailActivity)
            adapter = this@SessionDetailActivity.adapter
            setupRoundedList { it is SessionResultsAdapter.ResultHolder && !it.last }
        }
        intent.toSession(id)?.let(::bindSession)
        lifecycleScope.launch {
            results = repository.sessionResults(id)
            render()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.sessions
                    .map { sessions -> sessions.firstOrNull { it.id == id } }
                    .distinctUntilChanged()
                    .collect { if (it == null) finish() else bindSession(it) }
            }
        }
    }

    private fun bindSession(value: ScanSession) {
        session = value
        val trigger = getString(if (value.trigger == ScanTrigger.AUTO) R.string.session_trigger_auto else R.string.session_trigger_manual)
        binding.toolbarLayout.setTitle(formatStamp(this, value.startedAt))
        binding.toolbarLayout.setSubtitle(resources.getQuantityString(R.plurals.session_subtitle, value.deviceCount, trigger, value.deviceCount))
        render()
    }

    private fun render() {
        val current = session ?: return
        adapter.submitList(buildList {
            add(SessionItem.Summary(current.newCount, current.upToDateCount, current.failedCount))
            if (results.isNotEmpty()) {
                add(SessionItem.Header(getString(R.string.session_results)))
                results.forEachIndexed { index, result -> add(SessionItem.Result(result, index == results.lastIndex)) }
            }
        })
    }

    private fun Intent.toSession(id: Long): ScanSession? {
        if (!hasExtra(EXTRA_STARTED_AT)) return null
        return ScanSession(
            id = id,
            startedAt = getLongExtra(EXTRA_STARTED_AT, 0L),
            trigger = ScanTrigger.entries.getOrElse(getIntExtra(EXTRA_TRIGGER, 0)) { ScanTrigger.MANUAL },
            deviceCount = getIntExtra(EXTRA_DEVICE_COUNT, 0),
            newCount = getIntExtra(EXTRA_NEW_COUNT, 0),
            upToDateCount = getIntExtra(EXTRA_UP_TO_DATE_COUNT, 0),
            failedCount = getIntExtra(EXTRA_FAILED_COUNT, 0),
            firstModel = null,
            firstCsc = null
        )
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_STARTED_AT = "started_at"
        private const val EXTRA_TRIGGER = "trigger"
        private const val EXTRA_DEVICE_COUNT = "device_count"
        private const val EXTRA_NEW_COUNT = "new_count"
        private const val EXTRA_UP_TO_DATE_COUNT = "up_to_date_count"
        private const val EXTRA_FAILED_COUNT = "failed_count"

        fun intent(context: Context, session: ScanSession): Intent =
            Intent(context, SessionDetailActivity::class.java)
                .putExtra(EXTRA_SESSION_ID, session.id)
                .putExtra(EXTRA_STARTED_AT, session.startedAt)
                .putExtra(EXTRA_TRIGGER, session.trigger.ordinal)
                .putExtra(EXTRA_DEVICE_COUNT, session.deviceCount)
                .putExtra(EXTRA_NEW_COUNT, session.newCount)
                .putExtra(EXTRA_UP_TO_DATE_COUNT, session.upToDateCount)
                .putExtra(EXTRA_FAILED_COUNT, session.failedCount)
    }
}
