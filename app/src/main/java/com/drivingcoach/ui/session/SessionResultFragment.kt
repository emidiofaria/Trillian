package com.drivingcoach.ui.session

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.drivingcoach.BuildConfig
import com.drivingcoach.R
import com.drivingcoach.data.db.entity.ProcessingStatus
import com.drivingcoach.databinding.FragmentSessionResultBinding
import com.drivingcoach.util.SessionShareBuilder
import com.drivingcoach.util.ShareCardGenerator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class SessionResultFragment : Fragment() {

    @Inject
    lateinit var shareBuilder: SessionShareBuilder

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
                    shareSessionCard()
                    true
                }
                else -> false
            }
        }
        attachTelemetryExportGesture()
    }

    /**
     * Attaches the hidden developer export to a long-press on the share icon (SRS SH-07).
     *
     * A [android.view.MenuItem] has no long-click callback, so the inflated action view has
     * to be reached directly — and only once the toolbar has laid its menu out, hence the
     * [android.view.View.post]. Returning `true` consumes the event, which also suppresses
     * the tooltip the platform would otherwise show.
     *
     * The export is deliberately undiscoverable: the bundle contains a precise GPS trace of
     * the driver, so it must never be something a user taps by accident. The cost of hiding
     * it is that a refactor could silently detach this listener and nobody would notice until
     * the export was needed — which is exactly why `SessionShareTest` asserts that a
     * long-press really does emit the intent.
     */
    private fun attachTelemetryExportGesture() {
        binding.toolbar.post {
            _binding ?: return@post
            binding.toolbar.findViewById<View>(R.id.action_share)?.setOnLongClickListener {
                shareTelemetryBundle()
                true
            }
        }
    }
    
    private fun setupUploadStatusBar() {
        binding.dismissStatusButton.setOnClickListener {
            binding.uploadStatusBar.visibility = View.GONE
        }
    }

    private fun shareSessionCard() {
        val state = viewModel.uiState.value
        val session = state.session ?: run {
            showShareError(R.string.share_error_session_unavailable)
            return
        }
        val bestLap = state.bestLap

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    ShareCardGenerator.generate(
                        session = session,
                        bestLap = bestLap,
                        consistencyScore = state.consistencyScore,
                        context = requireContext()
                    )
                }

                val file = withContext(Dispatchers.IO) {
                    shareBuilder.pruneStaleArtifacts()
                    saveBitmapToCache(bitmap)
                }

                startChooser(
                    file = file,
                    mimeType = SessionShareBuilder.MIME_PNG,
                    text = getString(R.string.share_card_text, session.trackName),
                    chooserTitle = R.string.share_card_chooser_title
                )
            } catch (e: Exception) {
                // Previously swallowed in silence, which made a broken share button
                // indistinguishable from a dead one.
                Log.e(TAG, "Failed to share session card", e)
                showShareError(R.string.share_error_generic)
            }
        }
    }

    /**
     * Exports the telemetry diagnostic bundle (SRS SH-08, SH-09).
     *
     * Reads only: the bundle is assembled from a copy of the telemetry file and the session
     * records, and nothing about the recorded session is altered (SRS SH-11).
     */
    private fun shareTelemetryBundle() {
        val state = viewModel.uiState.value
        val session = state.session ?: run {
            showShareError(R.string.share_error_session_unavailable)
            return
        }

        Snackbar.make(binding.root, R.string.share_export_preparing, Snackbar.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                shareBuilder.buildTelemetryBundle(session, state.laps)
            }

            when (result) {
                is SessionShareBuilder.ShareResult.Success -> startChooser(
                    file = result.file,
                    mimeType = SessionShareBuilder.MIME_ZIP,
                    text = getString(R.string.share_export_text, session.trackName),
                    chooserTitle = R.string.share_export_chooser_title
                )

                is SessionShareBuilder.ShareResult.Failure -> showShareError(
                    when (result.reason) {
                        SessionShareBuilder.Reason.TELEMETRY_FILE_MISSING ->
                            R.string.share_error_telemetry_missing
                        SessionShareBuilder.Reason.EXPORT_FAILED ->
                            R.string.share_error_export_failed
                    }
                )
            }
        }
    }

    private fun startChooser(
        file: File,
        mimeType: String,
        text: String,
        @StringRes chooserTitle: Int
    ) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, getString(chooserTitle)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start share chooser", e)
            showShareError(R.string.share_error_generic)
        }
    }

    private fun showShareError(@StringRes message: Int) {
        _binding ?: return
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun saveBitmapToCache(bitmap: Bitmap): File {
        val cacheDir = shareBuilder.shareCacheDir()

        val file = File(cacheDir, "${SessionShareBuilder.CARD_PREFIX}${System.currentTimeMillis()}.png")
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
                3 -> "ANALYSIS"
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
            // NF-20: with upload disabled there is nothing pending, nothing to
            // retry and nothing to tap. Showing "Offline - tap to upload" or
            // "Uploading session..." would promise a transfer that cannot happen
            // and contradict the Play data-safety declaration. The local
            // processing states below are unaffected -- they never involved the
            // network.
            !BuildConfig.UPLOAD_ENABLED &&
                (state.hasLocalOnlyLaps ||
                    status == ProcessingStatus.FAILED ||
                    status == ProcessingStatus.UPLOADING ||
                    status == ProcessingStatus.PENDING) -> {
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

    private companion object {
        const val TAG = "SessionResultFragment"
    }
}
