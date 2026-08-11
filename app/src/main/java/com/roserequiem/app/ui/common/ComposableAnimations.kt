package com.roserequiem.app.ui.common

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import com.roserequiem.app.util.ui.materialSharedAxisXIn
import com.roserequiem.app.util.ui.materialSharedAxisXOut
import com.roserequiem.app.util.ui.materialSharedAxisYIn
import com.roserequiem.app.util.ui.materialSharedAxisYOut

val AnimatedTextContentTransformation = ContentTransform(
    materialSharedAxisXIn(initialOffsetX = { it / 10 }),
    materialSharedAxisXOut(targetOffsetX = { -it / 10 }),
    sizeTransform = SizeTransform(clip = false)
)

val AnimatedCardContentTransformation = ContentTransform(
    materialSharedAxisYIn(initialOffsetY = { it / 10 }),
    materialSharedAxisYOut(targetOffsetY = { -it / 10 }),
    sizeTransform = SizeTransform(clip = false)
)