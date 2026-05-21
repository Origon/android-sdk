package origon.example.android.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ai.origon.sdk.SessionSummary
import origon.example.android.R

/**
 * Flat list of past sessions for the sidebar. Highlights the selected
 * row. (The iOS app groups by day; the example keeps a flat list for
 * brevity — the SDK call, `getSessions()`, is the part that matters.)
 */
class SessionsAdapter(
    private val onClick: (sessionId: String) -> Unit,
) : RecyclerView.Adapter<SessionsAdapter.VH>() {

    private val items = mutableListOf<SessionSummary>()
    private var selectedId: String? = null

    fun submit(sessions: List<SessionSummary>, selected: String?) {
        items.clear()
        items.addAll(sessions)
        selectedId = selected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return VH(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val subject = itemView as TextView

        fun bind(session: SessionSummary) {
            subject.text = session.subject.ifEmpty {
                itemView.context.getString(R.string.sidebar_untitled)
            }
            subject.setBackgroundResource(
                if (session.sessionId == selectedId) R.drawable.bg_session_selected else 0
            )
            itemView.setOnClickListener { onClick(session.sessionId) }
        }
    }
}
