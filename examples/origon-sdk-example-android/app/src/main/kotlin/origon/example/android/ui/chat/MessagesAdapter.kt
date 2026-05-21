package origon.example.android.ui.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import ai.origon.sdk.Message
import ai.origon.sdk.MessageRole
import ai.origon.sdk.MessageStatus
import origon.example.android.R

/**
 * Renders the chat transcript. A message is "self" (right-aligned, accent
 * bubble) when its role is EXTERNAL — matching the iOS MessageBubble,
 * where the local user posts as the external participant.
 */
class MessagesAdapter(
    private val onAttachmentClick: (url: String) -> Unit,
) : RecyclerView.Adapter<MessagesAdapter.VH>() {

    private val items = mutableListOf<Message>()

    fun submit(messages: List<Message>) {
        items.clear()
        items.addAll(messages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return VH(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val row: LinearLayout = itemView.findViewById(R.id.message_row)
        private val bubble: TextView = itemView.findViewById(R.id.bubble_text)
        private val attachments: LinearLayout = itemView.findViewById(R.id.attachments_container)
        private val failed: TextView = itemView.findViewById(R.id.failed_label)

        fun bind(message: Message) {
            val isSelf = message.role == MessageRole.EXTERNAL
            val ctx = itemView.context

            row.gravity = if (isSelf) Gravity.END else Gravity.START

            val bodyText = message.text
            if (!bodyText.isNullOrEmpty()) {
                bubble.isVisible = true
                bubble.text = bodyText
                bubble.setBackgroundResource(
                    if (isSelf) R.drawable.bg_bubble_self else R.drawable.bg_bubble_remote
                )
                bubble.setTextColor(
                    ContextCompat.getColor(
                        ctx,
                        if (isSelf) R.color.white else R.color.origon_text_primary,
                    )
                )
            } else {
                bubble.isVisible = false
            }

            // Attachment chips — tappable filename rows that open the URL.
            attachments.removeAllViews()
            attachments.isVisible = message.attachments.isNotEmpty()
            for (att in message.attachments) {
                val chip = TextView(ctx).apply {
                    text = att.name.ifEmpty { "Attachment" }
                    setTextColor(ContextCompat.getColor(ctx, if (isSelf) R.color.white else R.color.origon_text_primary))
                    textSize = 14f
                    setBackgroundResource(if (isSelf) R.drawable.bg_bubble_self else R.drawable.bg_bubble_remote)
                    setPadding(28, 18, 28, 18)
                    setOnClickListener { att.url.takeIf { it.isNotEmpty() }?.let(onAttachmentClick) }
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = 6
                    gravity = if (isSelf) Gravity.END else Gravity.START
                }
                attachments.addView(chip, lp)
            }

            failed.isVisible = message.status == MessageStatus.FAILED
            if (failed.isVisible) {
                failed.text = message.errorText?.takeIf { it.isNotEmpty() } ?: "Failed to send"
            }
        }
    }
}
