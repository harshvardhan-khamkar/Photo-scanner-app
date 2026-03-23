package com.weddingmemory.app.ui.unlock

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.weddingmemory.app.BuildConfig
import com.weddingmemory.app.R
import com.weddingmemory.app.databinding.FragmentAlbumUnlockBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * AlbumUnlockFragment — entry screen where guests type their album code.
 *
 * Observes [UnlockUiState]:
 *   Idle         → reset UI
 *   Loading      → progress bar + disabled button (unlock in flight)
 *   Initializing → progress bar + status message (frames downloading)
 *   Success      → navigate to ScannerFragment
 *   Error        → show error message, re-enable button
 */
@AndroidEntryPoint
class AlbumUnlockFragment : Fragment() {

    private var _binding: FragmentAlbumUnlockBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AlbumUnlockViewModel by viewModels()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAlbumUnlockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
        observeUiState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // -------------------------------------------------------------------------
    // UI setup
    // -------------------------------------------------------------------------

    private fun setupUi() {
        binding.btnUnlock.setOnClickListener { submitCode() }

        binding.btnForgotCode.setOnClickListener {
            val url = BuildConfig.API_BASE_URL + "forgot-code"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        binding.etAlbumCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitCode()
                true
            } else false
        }
    }

    private fun submitCode() {
        val code = binding.etAlbumCode.text?.toString().orEmpty().trim()
        viewModel.onUnlockClicked(code)
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

    private fun render(state: UnlockUiState) {
        when (state) {
            // -----------------------------------------------------------------
            is UnlockUiState.Idle -> {
                binding.progressBar.isVisible = false
                binding.btnUnlock.isEnabled = true
                binding.tvError.isVisible = false
            }

            // -----------------------------------------------------------------
            is UnlockUiState.Loading -> {
                binding.progressBar.isVisible = true
                binding.btnUnlock.isEnabled = false
                binding.tvError.isVisible = false
            }

            // -----------------------------------------------------------------
            // Initialization in progress — keep spinner alive, show step message
            // in the same tvError view but coloured as info rather than error.
            is UnlockUiState.Initializing -> {
                binding.progressBar.isVisible = true
                binding.btnUnlock.isEnabled = false
                binding.tvError.isVisible = true
                binding.tvError.setTextColor(
                    requireContext().getColor(android.R.color.holo_blue_light)
                )
                binding.tvError.text = state.message
            }

            // -----------------------------------------------------------------
            is UnlockUiState.Success -> {
                binding.progressBar.isVisible = false
                binding.btnUnlock.isEnabled = true
                binding.tvError.isVisible = false
                val bundle = Bundle().apply {
                    putString("albumId", state.albumId)
                }
                findNavController().navigate(
                    R.id.action_albumUnlockFragment_to_scannerFragment,
                    bundle,
                )
                viewModel.resetState()
            }

            // -----------------------------------------------------------------
            is UnlockUiState.Error -> {
                binding.progressBar.isVisible = false
                binding.btnUnlock.isEnabled = true
                binding.tvError.isVisible = true
                binding.tvError.setTextColor(
                    requireContext().getColor(android.R.color.holo_red_light)
                )
                binding.tvError.text = state.message
                viewModel.resetState()
            }
        }
    }
}
