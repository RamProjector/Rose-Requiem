package com.roserequiem.app.ui.screens.init.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.roserequiem.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitScreenTopBar() {
    LargeTopAppBar(
        title = {
            Text(
                text = stringResource(id = R.string.init_screen_title)
            )
        }
    )
}