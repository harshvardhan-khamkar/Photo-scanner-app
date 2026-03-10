package com.weddingmemory.app.ui.scanner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.weddingmemory.app.R
import com.weddingmemory.app.databinding.FragmentScannerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class ScannerFragment : Fragment() {

    private var _binding: FragmentScannerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScannerViewModel by viewModels()

    // -------------------------------------------------------------------------
    // Permission launcher
    // -------------------------------------------------------------------------

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) bindCamera() else viewModel.onPermissionDenied()
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideSystemBars()
        observeUiState()

        // Pass album ID from nav args to the ViewModel
        val albumId = arguments?.getString("albumId").orEmpty()
        viewModel.setAlbumId(albumId)
        Timber.d("ScannerFragment: albumId=$albumId")

        requestCameraPermission()
        binding.btnOpenSettings.setOnClickListener { openAppSettings() }

        // Back button → return to album code entry
        binding.btnBack.setOnClickListener {
            findNavController().navigate(R.id.action_scannerFragment_to_albumUnlockFragment)
        }
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

    private fun render(state: ScannerUiState) {
        when (state) {
            is ScannerUiState.Idle -> {
                binding.recognizingOverlay.isVisible = false
                binding.permissionDeniedLayout.isVisible = false
            }

            is ScannerUiState.Ready -> {
                binding.recognizingOverlay.isVisible = false
                binding.permissionDeniedLayout.isVisible = false
            }

            is ScannerUiState.Recognizing -> {
                // Don't show overlay — scanning happens silently in background.
                // Showing/hiding overlay every 500ms causes a "flash" effect.
                binding.permissionDeniedLayout.isVisible = false
            }

            is ScannerUiState.Matched -> {
                binding.recognizingOverlay.isVisible = false
                Timber.d(
                    "Scanner matched frameId=%s start=%dms duration=%dms",
                    state.frame.id,
                    state.frame.startTimeMs,
                    state.frame.durationMs
                )
                val bundle = Bundle().apply {
                    putString(
                        com.weddingmemory.app.ui.player.PlayerFragment.ARG_VIDEO_URL,
                        state.frame.videoUrl
                    )
                    putLong(
                        com.weddingmemory.app.ui.player.PlayerFragment.ARG_START_TIMESTAMP,
                        state.frame.startTimeMs
                    )
                    putLong(
                        com.weddingmemory.app.ui.player.PlayerFragment.ARG_CLIP_DURATION_MS,
                        state.frame.durationMs
                    )
                }
                findNavController().navigate(
                    R.id.action_scannerFragment_to_playerFragment,
                    bundle
                )
                viewModel.resetToReady()
            }

            is ScannerUiState.NoMatch -> {
                // No UI change — overlay hides, scanner continues automatically
                binding.recognizingOverlay.isVisible = false
            }

            is ScannerUiState.Error -> {
                binding.recognizingOverlay.isVisible = false
                binding.permissionDeniedLayout.isVisible = false
                Timber.e("Scanner error: ${state.message}")
            }

            is ScannerUiState.PermissionDenied -> {
                binding.recognizingOverlay.isVisible = false
                binding.permissionDeniedLayout.isVisible = true
            }
        }
    }

    // -------------------------------------------------------------------------
    // Camera
    // -------------------------------------------------------------------------

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Timber.d("Camera permission already granted — binding camera")
            bindCamera()
        } else {
            Timber.d("Camera permission not granted — launching request dialog")
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun bindCamera() {
        ProcessCameraProvider.getInstance(requireContext()).also { future ->
            future.addListener({
                runCatching { future.get() }.onSuccess { provider ->
                    viewModel.startCamera(provider, binding.previewView, viewLifecycleOwner)
                }.onFailure {
                    Timber.e(it, "CameraProvider failed")
                }
            }, ContextCompat.getMainExecutor(requireContext()))
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun hideSystemBars() {
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        })
    }
}
