package com.geminidict.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom SF Symbols-inspired minimalist rounded-stroke vector icons (24dp viewport).
 * Keeps APK lightweight with zero external icon-pack bloat.
 */
object AppleIcons {

    private val strokeColor = SolidColor(Color.Black)

    val MagnifyingGlass: ImageVector by lazy {
        ImageVector.Builder("MagnifyingGlass", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10.5f, 3.5f)
                arcTo(7f, 7f, 0f, true, true, 10.49f, 3.5f)
                close()
                moveTo(15.6f, 15.6f)
                lineTo(20.5f, 20.5f)
            }
        }.build()
    }

    val BookClosed: ImageVector by lazy {
        ImageVector.Builder("BookClosed", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5.5f, 4.5f)
                curveTo(5.5f, 3.4f, 6.4f, 2.5f, 7.5f, 2.5f)
                lineTo(18.5f, 2.5f)
                lineTo(18.5f, 18.5f)
                lineTo(7.5f, 18.5f)
                curveTo(6.4f, 18.5f, 5.5f, 19.4f, 5.5f, 20.5f)
                close()
                moveTo(5.5f, 20.5f)
                curveTo(5.5f, 21.1f, 6.1f, 21.5f, 7.5f, 21.5f)
                lineTo(18.5f, 21.5f)
                moveTo(9f, 6.5f)
                lineTo(15f, 6.5f)
            }
        }.build()
    }

    val GraduationCap: ImageVector by lazy {
        ImageVector.Builder("GraduationCap", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 4f)
                lineTo(2.5f, 9f)
                lineTo(12f, 14f)
                lineTo(21.5f, 9f)
                close()
                moveTo(6f, 11f)
                lineTo(6f, 16.5f)
                curveTo(6f, 18.2f, 8.7f, 19.5f, 12f, 19.5f)
                curveTo(15.3f, 19.5f, 18f, 18.2f, 18f, 16.5f)
                lineTo(18f, 11f)
                moveTo(21.5f, 9f)
                lineTo(21.5f, 15.5f)
            }
        }.build()
    }

    val CloudDownload: ImageVector by lazy {
        ImageVector.Builder("CloudDownload", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 18f)
                curveTo(4.2f, 18f, 2.5f, 16.1f, 2.5f, 13.7f)
                curveTo(2.5f, 11.6f, 4f, 9.8f, 6.1f, 9.5f)
                curveTo(6.7f, 6.6f, 9.2f, 4.5f, 12.2f, 4.5f)
                curveTo(15.5f, 4.5f, 18.1f, 7f, 18.4f, 10.2f)
                curveTo(20.2f, 10.6f, 21.5f, 12.1f, 21.5f, 14f)
                curveTo(21.5f, 16.2f, 19.8f, 18f, 17.5f, 18f)
                moveTo(12f, 12f)
                lineTo(12f, 20.5f)
                moveTo(9f, 17.5f)
                lineTo(12f, 20.5f)
                lineTo(15f, 17.5f)
            }
        }.build()
    }

    val GearShape: ImageVector by lazy {
        ImageVector.Builder("GearShape", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 8.8f)
                arcTo(3.2f, 3.2f, 0f, true, true, 11.99f, 8.8f)
                close()
                moveTo(12f, 2.5f)
                lineTo(12f, 4.8f)
                moveTo(12f, 19.2f)
                lineTo(12f, 21.5f)
                moveTo(4.8f, 12f)
                lineTo(2.5f, 12f)
                moveTo(21.5f, 12f)
                lineTo(19.2f, 12f)
                moveTo(6.9f, 6.9f)
                lineTo(5.3f, 5.3f)
                moveTo(18.7f, 18.7f)
                lineTo(17.1f, 17.1f)
                moveTo(17.1f, 6.9f)
                lineTo(18.7f, 5.3f)
                moveTo(5.3f, 18.7f)
                lineTo(6.9f, 17.1f)
                moveTo(12f, 5.4f)
                arcTo(6.6f, 6.6f, 0f, true, true, 11.99f, 5.4f)
                close()
            }
        }.build()
    }

    val SpeakerWave: ImageVector by lazy {
        ImageVector.Builder("SpeakerWave", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3.5f, 9.5f)
                lineTo(7.5f, 9.5f)
                lineTo(12.5f, 5.5f)
                lineTo(12.5f, 18.5f)
                lineTo(7.5f, 14.5f)
                lineTo(3.5f, 14.5f)
                close()
                moveTo(16f, 9f)
                curveTo(17.2f, 10.2f, 17.2f, 13.8f, 16f, 15f)
                moveTo(18.8f, 6.5f)
                curveTo(21.2f, 9.2f, 21.2f, 14.8f, 18.8f, 17.5f)
            }
        }.build()
    }

    val Sparkles: ImageVector by lazy {
        ImageVector.Builder("Sparkles", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = strokeColor,
                pathFillType = PathFillType.NonZero
            ) {
                // Main 4-point star
                moveTo(10.5f, 3f)
                lineTo(12.2f, 8.3f)
                lineTo(17.5f, 10f)
                lineTo(12.2f, 11.7f)
                lineTo(10.5f, 17f)
                lineTo(8.8f, 11.7f)
                lineTo(3.5f, 10f)
                lineTo(8.8f, 8.3f)
                close()
                // Accent smaller sparkle
                moveTo(18f, 13.5f)
                lineTo(18.9f, 16.1f)
                lineTo(21.5f, 17f)
                lineTo(18.9f, 17.9f)
                lineTo(18f, 20.5f)
                lineTo(17.1f, 17.9f)
                lineTo(14.5f, 17f)
                lineTo(17.1f, 16.1f)
                close()
            }
        }.build()
    }

    val SquareArrowDown: ImageVector by lazy {
        ImageVector.Builder("SquareArrowDown", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.5f)
                lineTo(12f, 14.5f)
                moveTo(8.5f, 11f)
                lineTo(12f, 14.5f)
                lineTo(15.5f, 11f)
                moveTo(4.5f, 13.5f)
                lineTo(4.5f, 18.5f)
                curveTo(4.5f, 19.6f, 5.4f, 20.5f, 6.5f, 20.5f)
                lineTo(17.5f, 20.5f)
                curveTo(18.6f, 20.5f, 19.5f, 19.6f, 19.5f, 18.5f)
                lineTo(19.5f, 13.5f)
            }
        }.build()
    }

    val SquareArrowUp: ImageVector by lazy {
        ImageVector.Builder("SquareArrowUp", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 14.5f)
                lineTo(12f, 3.5f)
                moveTo(8.5f, 7f)
                lineTo(12f, 3.5f)
                lineTo(15.5f, 7f)
                moveTo(4.5f, 12.5f)
                lineTo(4.5f, 18.5f)
                curveTo(4.5f, 19.6f, 5.4f, 20.5f, 6.5f, 20.5f)
                lineTo(17.5f, 20.5f)
                curveTo(18.6f, 20.5f, 19.5f, 19.6f, 19.5f, 18.5f)
                lineTo(19.5f, 12.5f)
            }
        }.build()
    }

    val HeartOutline: ImageVector by lazy {
        ImageVector.Builder("HeartOutline", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 20.2f)
                curveTo(12f, 20.2f, 3.5f, 14.8f, 3.5f, 8.8f)
                curveTo(3.5f, 5.8f, 5.8f, 3.8f, 8.4f, 3.8f)
                curveTo(10.1f, 3.8f, 11.4f, 4.7f, 12f, 6f)
                curveTo(12.6f, 4.7f, 13.9f, 3.8f, 15.6f, 3.8f)
                curveTo(18.2f, 3.8f, 20.5f, 5.8f, 20.5f, 8.8f)
                curveTo(20.5f, 14.8f, 12f, 20.2f, 12f, 20.2f)
                close()
            }
        }.build()
    }

    val HeartFill: ImageVector by lazy {
        ImageVector.Builder("HeartFill", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = strokeColor) {
                moveTo(12f, 20.5f)
                curveTo(12f, 20.5f, 3.2f, 15f, 3.2f, 8.8f)
                curveTo(3.2f, 5.6f, 5.6f, 3.5f, 8.4f, 3.5f)
                curveTo(10.1f, 3.5f, 11.4f, 4.4f, 12f, 5.7f)
                curveTo(12.6f, 4.4f, 13.9f, 3.5f, 15.6f, 3.5f)
                curveTo(18.4f, 3.5f, 20.8f, 5.6f, 20.8f, 8.8f)
                curveTo(20.8f, 15f, 12f, 20.5f, 12f, 20.5f)
                close()
            }
        }.build()
    }

    val Plus: ImageVector by lazy {
        ImageVector.Builder("Plus", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }.build()
    }

    val Xmark: ImageVector by lazy {
        ImageVector.Builder("Xmark", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(6.5f, 6.5f)
                lineTo(17.5f, 17.5f)
                moveTo(17.5f, 6.5f)
                lineTo(6.5f, 17.5f)
            }
        }.build()
    }

    val Pencil: ImageVector by lazy {
        ImageVector.Builder("Pencil", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 20f)
                lineTo(8.2f, 19f)
                lineTo(19.2f, 8f)
                curveTo(20f, 7.2f, 20f, 5.9f, 19.2f, 5.1f)
                lineTo(18.9f, 4.8f)
                curveTo(18.1f, 4f, 16.8f, 4f, 16f, 4.8f)
                lineTo(5f, 15.8f)
                close()
            }
        }.build()
    }

    val Trash: ImageVector by lazy {
        ImageVector.Builder("Trash", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4.5f, 6.5f)
                lineTo(19.5f, 6.5f)
                moveTo(9.5f, 6.5f)
                lineTo(9.5f, 4.5f)
                curveTo(9.5f, 3.9f, 10f, 3.5f, 10.6f, 3.5f)
                lineTo(13.4f, 3.5f)
                curveTo(14f, 3.5f, 14.5f, 3.9f, 14.5f, 4.5f)
                lineTo(14.5f, 6.5f)
                moveTo(6.5f, 6.5f)
                lineTo(7.4f, 19f)
                curveTo(7.5f, 20f, 8.3f, 20.5f, 9.3f, 20.5f)
                lineTo(14.7f, 20.5f)
                curveTo(15.7f, 20.5f, 16.5f, 20f, 16.6f, 19f)
                lineTo(17.5f, 6.5f)
                moveTo(10.2f, 10.5f)
                lineTo(10.2f, 16.5f)
                moveTo(13.8f, 10.5f)
                lineTo(13.8f, 16.5f)
            }
        }.build()
    }

    val ArrowClockwise: ImageVector by lazy {
        ImageVector.Builder("ArrowClockwise", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19.5f, 12f)
                arcTo(7.5f, 7.5f, 0f, true, true, 17.3f, 6.7f)
                moveTo(17.5f, 3.5f)
                lineTo(17.5f, 7.5f)
                lineTo(13.5f, 7.5f)
            }
        }.build()
    }

    val CheckmarkCircle: ImageVector by lazy {
        ImageVector.Builder("CheckmarkCircle", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 11.99f, 3f)
                close()
                moveTo(8.3f, 12.2f)
                lineTo(10.8f, 14.7f)
                lineTo(15.9f, 9.6f)
            }
        }.build()
    }
}
