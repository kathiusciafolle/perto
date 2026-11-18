package com.bitchat.android.core.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Perto: marca do app dentro das telas. Um balão de conversa com um ponto vazado no meio
 * (o "você" do radar). Mantém o nome BitChatIcon para não mexer em quem usa.
 */
val BitChatIcon: ImageVector
    get() {
        _BitChatIcon?.let { return it }

        return ImageVector.Builder(
            name = "PertoIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                // balão
                moveTo(7f, 3f)
                lineTo(17f, 3f)
                arcTo(5f, 5f, 0f, false, true, 22f, 8f)
                lineTo(22f, 12f)
                arcTo(5f, 5f, 0f, false, true, 17f, 17f)
                lineTo(10f, 17f)
                lineTo(5f, 21.5f)
                lineTo(5.6f, 16.8f)
                arcTo(5f, 5f, 0f, false, true, 2f, 12f)
                lineTo(2f, 8f)
                arcTo(5f, 5f, 0f, false, true, 7f, 3f)
                close()
                // ponto vazado no meio
                moveTo(9.4f, 10f)
                arcTo(2.6f, 2.6f, 0f, true, false, 14.6f, 10f)
                arcTo(2.6f, 2.6f, 0f, true, false, 9.4f, 10f)
                close()
            }
        }.build().also { _BitChatIcon = it }
    }

private var _BitChatIcon: ImageVector? = null
