package com.drivingcoach.testing

import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Test activity for Hilt fragment testing.
 * 
 * This activity is used by launchFragmentInHiltContainer to host
 * fragments that require Hilt dependency injection.
 */
@AndroidEntryPoint
class HiltTestActivity : AppCompatActivity()
