package origon.example.android.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import origon.example.android.R
import origon.example.android.data.PendingAttachment

/** Horizontal row of composer upload tiles. Mirrors iOS AttachmentTile. */
class PendingAttachmentsAdapter(
    private val onRemove: (id: String) -> Unit,
) : RecyclerView.Adapter<PendingAttachmentsAdapter.VH>() {

    private val items = mutableListOf<PendingAttachment>()

    fun submit(attachments: List<PendingAttachment>) {
        items.clear()
        items.addAll(attachments)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_attachment, parent, false)
        return VH(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.tile_image)
        private val fileGroup: LinearLayout = itemView.findViewById(R.id.tile_file)
        private val filename: TextView = itemView.findViewById(R.id.tile_filename)
        private val overlay: View = itemView.findViewById(R.id.tile_overlay)
        private val progress: ProgressBar = itemView.findViewById(R.id.tile_progress)
        private val error: ImageView = itemView.findViewById(R.id.tile_error)
        private val remove: ImageView = itemView.findViewById(R.id.tile_remove)

        fun bind(att: PendingAttachment) {
            if (att.isImage && att.previewUri != null) {
                image.isVisible = true
                fileGroup.isVisible = false
                image.load(att.previewUri)
            } else {
                image.isVisible = false
                fileGroup.isVisible = true
                filename.text = att.fileName
            }

            when (att.status) {
                PendingAttachment.Status.UPLOADING -> {
                    overlay.isVisible = true
                    progress.isVisible = true
                    progress.progress = att.progress
                    error.isVisible = false
                }
                PendingAttachment.Status.ERROR -> {
                    overlay.isVisible = true
                    progress.isVisible = false
                    error.isVisible = true
                }
                PendingAttachment.Status.COMPLETED -> {
                    overlay.isVisible = false
                    progress.isVisible = false
                    error.isVisible = false
                }
            }

            remove.setOnClickListener { onRemove(att.id) }
        }
    }
}
