package com.drivingcoach.ui.tracklist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.drivingcoach.databinding.ItemTrackBinding

class TrackAdapter(
    private val onClick: (TrackListItem) -> Unit,
    private val onLongClick: (TrackListItem) -> Unit
) : ListAdapter<TrackListItem, TrackAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTrackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemTrackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TrackListItem) {
            binding.trackName.text = item.name
            binding.trackDetails.text = item.details
            binding.trackDetails.isVisible = item.details.isNotEmpty()
            binding.bundledBadge.isVisible = item.isBundled

            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener {
                // Only saved circuits can be renamed or deleted, so a long press on a
                // bundled one is left unhandled rather than opening a menu of two
                // actions that would both be refused.
                if (item.isBundled) {
                    false
                } else {
                    onLongClick(item)
                    true
                }
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<TrackListItem>() {
            override fun areItemsTheSame(oldItem: TrackListItem, newItem: TrackListItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: TrackListItem, newItem: TrackListItem) =
                oldItem == newItem
        }
    }
}
