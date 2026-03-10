package com.weddingmemory.app.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.weddingmemory.app.databinding.FragmentPlayerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * PlayerFragment — full-screen video playback.
 *
 * Receives [ARG_VIDEO_URL] and [ARG_START_TIMESTAMP] from [ScannerFragment]
 * via the navigation bundle, passes them to [PlayerViewModel] which owns
 * the ExoPlayer instance across configuration changes.
 *
 * Lifecycle:
 *  - onStart  → attach player to PlayerView
 *  - onStop   → detach player (surface released, audio focus returned)
 *  - ViewModel.onCleared → ExoPlayer.release() (back-press or process death)
 */
@AndroidEntryPoint
class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlayerViewModel by viewModels()

    companion object {
        const val ARG_VIDEO_URL       = "videoUrl"
        const val ARG_START_TIMESTAMP = "startTimestamp"
        const val ARG_CLIP_DURATION_MS = "clipDurationMs"
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val videoUrl       = arguments?.getString(ARG_VIDEO_URL).orEmpty()
        val startTimestamp = arguments?.getLong(ARG_START_TIMESTAMP) ?: 0L
        val clipDurationMs = arguments?.getLong(ARG_CLIP_DURATION_MS) ?: 0L

        Timber.d(
            "PlayerFragment args start=%dms duration=%dms url=%s",
            startTimestamp,
            clipDurationMs,
            videoUrl
        )
        viewModel.prepareVideo(videoUrl, startTimestamp, clipDurationMs)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        observeUiState()
    }

    override fun onStart() {
        super.onStart()
        // Attach player to the surface every time the fragment is visible
        binding.playerView.player = viewModel.player
    }

    override fun onStop() {
        super.onStop()
        // Detach to release surface and return audio focus while off-screen
        binding.playerView.player = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // -------------------------------------------------------------------------
    // State observation
    // -------------------------------------------------------------------------

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: PlayerUiState) {
        when (state) {
            is PlayerUiState.Idle -> {
                binding.bufferingIndicator.isVisible = false
                binding.tvPlayerError.isVisible = false
            }

            is PlayerUiState.Buffering -> {
                binding.bufferingIndicator.isVisible = true
                binding.tvPlayerError.isVisible = false
            }

            is PlayerUiState.Playing -> {
                binding.bufferingIndicator.isVisible = false
                binding.tvPlayerError.isVisible = false
            }

            is PlayerUiState.Ended -> {
                binding.bufferingIndicator.isVisible = false
                binding.tvPlayerError.isVisible = false
                // Auto-return to scanner when video finishes
                findNavController().popBackStack()
            }

            is PlayerUiState.Error -> {
                binding.bufferingIndicator.isVisible = false
                binding.tvPlayerError.isVisible = true
                binding.tvPlayerError.text = state.message
            }
        }
    }
}
