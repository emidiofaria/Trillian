package com.drivingcoach.ui.tracklist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.drivingcoach.databinding.FragmentTrackListBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The circuits the app knows about, for a session that has already been named.
 *
 * Picking one does not start recording. It leads to [TrackConfirmFragment], which
 * carries the GPS readiness gate that Track Setup used to provide on this path.
 */
@AndroidEntryPoint
class TrackListFragment : Fragment() {

    private var _binding: FragmentTrackListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrackListViewModel by viewModels()
    private val args: TrackListFragmentArgs by navArgs()

    private var contextDialog: androidx.appcompat.app.AlertDialog? = null

    private val adapter by lazy {
        TrackAdapter(
            onClick = { item ->
                findNavController().navigate(
                    TrackListFragmentDirections.actionTrackListToTrackConfirm(
                        sessionName = args.sessionName,
                        trackId = item.id
                    )
                )
            },
            onLongClick = ::showContextMenu
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.subtitle.text = "Session: ${args.sessionName}"
        binding.trackList.layoutManager = LinearLayoutManager(requireContext())
        binding.trackList.adapter = adapter

        binding.newCircuitButton.setOnClickListener {
            findNavController().navigate(
                TrackListFragmentDirections.actionTrackListToTrackSetup(args.sessionName)
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tracks.collectLatest { tracks ->
                    adapter.submitList(tracks)
                    binding.emptyMessage.isVisible = tracks.isEmpty()
                }
            }
        }
    }

    private fun showContextMenu(item: TrackListItem) {
        contextDialog?.dismiss()
        contextDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.name)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameDialog(item)
                    1 -> showDeleteConfirmation(item)
                }
            }
            .show()
    }

    private fun showRenameDialog(item: TrackListItem) {
        val editText = EditText(requireContext()).apply {
            setText(item.name)
            setSelection(item.name.length)
            setPadding(64, 32, 64, 32)
            setTextColor(0xFFFFFFFF.toInt())
        }

        contextDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename Circuit")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                viewModel.rename(item.id, editText.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(item: TrackListItem) {
        contextDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Circuit")
            .setMessage(
                "Delete \"${item.name}\"?\n\nSessions already recorded here are kept. " +
                    "You would need to set the start/finish line again to use this circuit."
            )
            .setPositiveButton("Delete") { _, _ -> viewModel.delete(item.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        contextDialog?.dismiss()
        contextDialog = null
        binding.trackList.adapter = null
        _binding = null
    }
}
