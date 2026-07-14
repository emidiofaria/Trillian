package com.drivingcoach.ui.session.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.drivingcoach.R
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.databinding.FragmentLapsBinding
import com.drivingcoach.databinding.ItemLapCardBinding
import com.drivingcoach.ui.session.SessionResultFragmentDirections
import com.drivingcoach.ui.session.SessionResultViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LapsFragment : Fragment() {

    private var _binding: FragmentLapsBinding? = null
    private val binding get() = _binding!!

    private val parentViewModel: SessionResultViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private val adapter = LapAdapter { lap ->
        // Navigate to lap detail using parent's nav controller
        val parentFragment = requireParentFragment()
        val navController = parentFragment.findNavController()
        val action = SessionResultFragmentDirections
            .actionSessionResultToLapDetail(lap.id)
        navController.navigate(action)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLapsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        binding.lapsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@LapsFragment.adapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parentViewModel.uiState.collect { state ->
                    val bestLap = state.bestLap
                    adapter.setBestLap(bestLap)
                    adapter.submitList(state.laps)
                    
                    binding.emptyStateText.visibility = 
                        if (state.laps.isEmpty() && !state.isLoading) View.VISIBLE else View.GONE
                    binding.lapsRecyclerView.visibility = 
                        if (state.laps.isNotEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SESSION_ID = "sessionId"

        fun newInstance(sessionId: Long): LapsFragment {
            return LapsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SESSION_ID, sessionId)
                }
            }
        }
    }
}

private class LapAdapter(
    private val onLapClick: (LapEntity) -> Unit
) : ListAdapter<LapEntity, LapAdapter.LapViewHolder>(LapDiffCallback()) {

    private var bestLap: LapEntity? = null

    fun setBestLap(lap: LapEntity?) {
        bestLap = lap
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LapViewHolder {
        val binding = ItemLapCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LapViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LapViewHolder, position: Int) {
        holder.bind(getItem(position), bestLap)
    }

    inner class LapViewHolder(
        private val binding: ItemLapCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onLapClick(getItem(position))
                }
            }
        }

        fun bind(lap: LapEntity, bestLap: LapEntity?) {
            val context = binding.root.context
            val isBest = lap.id == bestLap?.id

            binding.lapNumber.text = "LAP ${lap.lapNumber}"
            binding.lapTime.text = formatLapTime(lap.durationMs)
            
            // Best lap indicator
            binding.bestLapIndicator.visibility = if (isBest) View.VISIBLE else View.GONE
            binding.bestBadge.visibility = if (isBest) View.VISIBLE else View.GONE

            // Delta vs best
            if (isBest || bestLap == null) {
                binding.deltaTime.visibility = View.GONE
            } else {
                binding.deltaTime.visibility = View.VISIBLE
                val deltaSec = (lap.durationMs - bestLap.durationMs) / 1000.0
                if (deltaSec > 0) {
                    binding.deltaTime.text = String.format("+%.3f", deltaSec)
                    binding.deltaTime.setTextColor(
                        ContextCompat.getColor(context, R.color.colorDeltaNegative)
                    )
                } else {
                    binding.deltaTime.text = String.format("%.3f", deltaSec)
                    binding.deltaTime.setTextColor(
                        ContextCompat.getColor(context, R.color.colorDeltaPositive)
                    )
                }
            }

            // Sector times
            binding.sector1Time.text = formatSectorTime(lap.sector1Ms)
            binding.sector2Time.text = formatSectorTime(lap.sector2Ms)
            binding.sector3Time.text = formatSectorTime(lap.sector3Ms)
        }

        private fun formatLapTime(durationMs: Long): String {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val millis = durationMs % 1000
            return String.format("%d:%02d.%03d", minutes, seconds, millis)
        }

        private fun formatSectorTime(ms: Long): String {
            val seconds = ms / 1000.0
            return String.format("%.1fs", seconds)
        }
    }
}

private class LapDiffCallback : DiffUtil.ItemCallback<LapEntity>() {
    override fun areItemsTheSame(oldItem: LapEntity, newItem: LapEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: LapEntity, newItem: LapEntity): Boolean {
        return oldItem == newItem
    }
}
