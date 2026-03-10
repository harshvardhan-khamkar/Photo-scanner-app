package com.weddingmemory.app.ui.main

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.weddingmemory.app.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity — single-activity shell for the Wedding Memory Platform.
 *
 * This activity owns the top-level NavHostFragment and acts purely as a
 * container. All screen logic lives in Fragments + ViewModels.
 *
 * Step 1: Empty shell with ViewBinding wired up.
 * Step 2+: Navigation graph, deep-linking, and permission handling will be added.
 *
 * Architecture notes:
 *  - @AndroidEntryPoint enables Hilt injection in this activity.
 *  - ViewBinding replaces all findViewById() calls.
 *  - Do NOT add business logic here — delegate to ViewModels and Fragments.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // ViewBinding — inflated once, never reassigned.
    private lateinit var binding: ActivityMainBinding

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Navigation, toolbar, and deep-link setup will be added in Step 2.
    }
}
