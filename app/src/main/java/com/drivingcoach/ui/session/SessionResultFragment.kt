package com.drivingcoach.ui.session

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.drivingcoach.R
import com.drivingcoach.data.db.entity.ProcessingStatus
import com.drivingcoach.databinding.FragmentSessionResultBinding
import com.drivingcoach.util.ShareCardGenerator
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class SessionResultFragment : Fragment() {

    private var _binding: FragmentSessionResultBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SessionResultViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSessionResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupViewPager()
        setupRetryButton()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        // Inflate menu and handle share action
        binding.toolbar.inflateMenu(R.menu.menu_session_result)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_share -> {
                    shareSession()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupRetryButton() {
        binding.retryButton.setOnClickListener {
            viewModel.retryAnalysis()
        }
    }

    private fun shareSession() {
        val state = viewModel.uiState.value
        val session = state.session ?: return
        val bestLap = state.bestLap

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Generate the share card bitmap
                val bitmap = withContext(Dispatchers.Default) {
                    ShareCardGenerator.generate(
                        session = session,
                        bestLap = bestLap,
                        consistencyScore = state.consistencyScore,
                        context = requireContext()
                    )
                }

                // Save bitmap to cache
                val file = withContext(Dispatchers.IO) {
                    saveBitmapToCache(bitmap)
                }

                // Create share intent
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "Check out my lap time at ${session.trackName}! 🏎️")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(Intent.createChooser(shareIntent, "Share your achievement"))
            } catch (e: Exception) {
                // Handle error silently or show snackbar
            }
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap): File {
        val cacheDir = File(requireContext().cacheDir, "shared")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val file = File(cacheDir, "session_share_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    private fun setupViewPager() {
        val adapter = SessionPagerAdapter(this, viewModel.sessionId)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "LAPS"
                1 -> "COACH"
                2 -> "CHART"
                else -> ""
            }
        }.attach()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                    updateProcessingCard(state)
                }
            }
        }
    }

    private fun updateUI(state: SessionUiState) {
        binding.collapsingToolbar.title = state.session?.trackName ?: "Session"
        
        state.bestLap?.let { bestLap ->
            binding.bestLapTime.text = formatLapTime(bestLap.durationMs)
            binding.bestLapTime.visibility = View.VISIBLE
        } ?: run {
            binding.bestLapTime.visibility = View.GONE
        }
        
        // Update track name
        binding.trackName.text = state.session?.trackName ?: "Session"
        
        // Update session summary
        val lapCount = state.laps.size
        val consistency = String.format("%.1f%%", state.consistencyScore)
        binding.sessionSummary.text = "$lapCount laps • $consistency consistency"
    }
    
    private fun updateProcessingCard(state: SessionUiState) {
        val status = state.processingStatus
        
        when (status) {
            ProcessingStatus.COMPLETE -> {
                binding.processingCard.visibility = View.GONE
            }
            ProcessingStatus.FAILED -> {
                binding.processingCard.visibility = View.VISIBLE
                binding.processingTitle.text = "Processing failed"
                binding.processingTitle.setTextColor(resources.getColor(R.color.colorError, null))
                binding.processingProgress.visibility = View.GONE
                binding.retryButton.visibility = View.VISIBLE
                updateProcessingSteps(status)
            }
            else -> {
                binding.processingCard.visibility = View.VISIBLE
                binding.processingTitle.text = "Processing your session..."
                binding.processingTitle.setTextColor(resources.getColor(R.color.colorOnSurface, null))
                binding.processingProgress.visibility = View.VISIBLE
                binding.retryButton.visibility = View.GONE
                updateProcessingSteps(status)
            }
        }
    }
    
    private fun updateProcessingSteps(status: ProcessingStatus) {
        val checkMark = "✓"
        val pending = "○"
        val current = "●"
        
        when (status) {
            ProcessingStatus.PENDING -> {
                binding.stepUploading.text = "Uploading $current"
                binding.stepLaps.text = "Detecting laps $pending"
                binding.stepCoaching.text = "Generating coaching $pending"
            }
            ProcessingStatus.UPLOADING -> {
                binding.stepUploading.text = "Uploading $current"
                binding.stepLaps.text = "Detecting laps $pending"
                binding.stepCoaching.text = "Generating coaching $pending"
            }
            ProcessingStatus.DETECTING_LAPS -> {
                binding.stepUploading.text = "Uploading $checkMark"
                binding.stepLaps.text = "Detecting laps $current"
                binding.stepCoaching.text = "Generating coaching $pending"
            }
            ProcessingStatus.GENERATING_COACHING -> {
                binding.stepUploading.text = "Uploading $checkMark"
                binding.stepLaps.text = "Detecting laps $checkMark"
                binding.stepCoaching.text = "Generating coaching $current"
            }
            ProcessingStatus.COMPLETE -> {
                binding.stepUploading.text = "Uploading $checkMark"
                binding.stepLaps.text = "Detecting laps $checkMark"
                binding.stepCoaching.text = "Generating coaching $checkMark"
            }
            ProcessingStatus.FAILED -> {
                binding.stepUploading.text = "Uploading ✗"
                binding.stepLaps.text = "Detecting laps ✗"
                binding.stepCoaching.text = "Generating coaching ✗"
            }
        }
    }

    private fun formatLapTime(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = durationMs % 1000
        return String.format("%d:%02d.%03d", minutes, seconds, millis)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
