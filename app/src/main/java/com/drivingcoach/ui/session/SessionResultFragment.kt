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
        setupUploadStatusBar()
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
    
    private fun setupUploadStatusBar() {
        binding.dismissStatusButton.setOnClickListener {
            binding.uploadStatusBar.visibility = View.GONE
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
        
        // Update upload status bar
        updateUploadStatusBar(state)
    }
    
    private fun updateUploadStatusBar(state: SessionUiState) {
        val status = state.processingStatus
        
        when {
            // Hide when complete
            status == ProcessingStatus.COMPLETE -> {
                binding.uploadStatusBar.visibility = View.GONE
            }
            // Show local-only message when offline with local laps
            state.hasLocalOnlyLaps && state.laps.isNotEmpty() -> {
                binding.uploadStatusBar.visibility = View.VISIBLE
                binding.uploadProgress.visibility = View.GONE
                binding.uploadStatusText.text = "📶 Offline • Tap to upload for AI coaching"
                binding.uploadStatusBar.setBackgroundColor(resources.getColor(R.color.colorPrimaryVariant, null))
            }
            // Show failed status
            status == ProcessingStatus.FAILED -> {
                binding.uploadStatusBar.visibility = View.VISIBLE
                binding.uploadProgress.visibility = View.GONE
                binding.uploadStatusText.text = "⚠️ Upload failed • Tap to retry"
                binding.uploadStatusBar.setBackgroundColor(resources.getColor(R.color.colorError, null))
            }
            // Show uploading status
            status == ProcessingStatus.UPLOADING || status == ProcessingStatus.PENDING -> {
                binding.uploadStatusBar.visibility = View.VISIBLE
                binding.uploadProgress.visibility = View.VISIBLE
                binding.uploadStatusText.text = "Uploading session..."
                binding.uploadStatusBar.setBackgroundColor(resources.getColor(R.color.colorPrimaryVariant, null))
            }
            // Show processing status
            status == ProcessingStatus.DETECTING_LAPS -> {
                binding.uploadStatusBar.visibility = View.VISIBLE
                binding.uploadProgress.visibility = View.VISIBLE
                binding.uploadStatusText.text = "Detecting laps..."
                binding.uploadStatusBar.setBackgroundColor(resources.getColor(R.color.colorPrimaryVariant, null))
            }
            status == ProcessingStatus.GENERATING_COACHING -> {
                binding.uploadStatusBar.visibility = View.VISIBLE
                binding.uploadProgress.visibility = View.VISIBLE
                binding.uploadStatusText.text = "Generating coaching insights..."
                binding.uploadStatusBar.setBackgroundColor(resources.getColor(R.color.colorPrimaryVariant, null))
            }
            else -> {
                binding.uploadStatusBar.visibility = View.GONE
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
