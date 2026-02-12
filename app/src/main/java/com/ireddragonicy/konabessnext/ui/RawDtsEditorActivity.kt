package com.ireddragonicy.konabessnext.ui

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import com.ireddragonicy.konabessnext.ui.compose.RawDtsScreen
import com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme
import com.ireddragonicy.konabessnext.utils.LocaleUtil
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RawDtsEditorActivity : AppCompatActivity() {

    private val sharedViewModel: SharedGpuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        sharedViewModel.loadData() 

        setContentView(ComposeView(this).apply {
            setContent {
                KonaBessTheme {
                    RawDtsScreen()
                }
            }
        })
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleUtil.wrap(newBase))
    }
}
