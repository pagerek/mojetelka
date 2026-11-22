package cz.webshare.tv.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Color as AndroidColor

data class AmbilightPalette(
    val topLeft: Color,
    val topRight: Color,
    val bottomLeft: Color,
    val bottomRight: Color
)

private val DefaultKidsAmbilightPalette = AmbilightPalette(
    topLeft = Color(0xFF00B4D8),     // Vivid cyan
    topRight = Color(0xFF9D4EDD),    // Electric violet
    bottomLeft = Color(0xFFFF6B6B),  // Warm coral
    bottomRight = Color(0xFFFF9F1C)  // Golden amber
)

/**
 * Renders a full-perimeter Philips Ambilight-optimized backdrop for the Kids tab.
 * Samples the 4 quadrants of the currently focused poster/backdrop so the TV's edge
 * Ambilight LEDs react dynamically as the user browses.
 */
@Composable
fun KidsAmbilightContainer(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    var palette by remember { mutableStateOf(DefaultKidsAmbilightPalette) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) {
            palette = DefaultKidsAmbilightPalette
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) {
            runCatching {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(48, 48)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request) as? SuccessResult ?: return@runCatching null
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
                extractQuadrantPalette(bitmap)
            }.getOrNull()
        }
        if (extracted != null) {
            palette = extracted
        }
    }

    val topLeft by animateColorAsState(palette.topLeft, tween(650), label = "ambi_tl")
    val topRight by animateColorAsState(palette.topRight, tween(650), label = "ambi_tr")
    val bottomLeft by animateColorAsState(palette.bottomLeft, tween(650), label = "ambi_bl")
    val bottomRight by animateColorAsState(palette.bottomRight, tween(650), label = "ambi_br")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090C14))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val radius = maxOf(w, h) * 0.72f

            // Top-left edge glow
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(topLeft.copy(alpha = 0.62f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = radius
                )
            )
            // Top-right edge glow
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(topRight.copy(alpha = 0.62f), Color.Transparent),
                    center = Offset(w, 0f),
                    radius = radius
                )
            )
            // Bottom-left edge glow
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(bottomLeft.copy(alpha = 0.52f), Color.Transparent),
                    center = Offset(0f, h),
                    radius = radius
                )
            )
            // Bottom-right edge glow
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(bottomRight.copy(alpha = 0.52f), Color.Transparent),
                    center = Offset(w, h),
                    radius = radius
                )
            )
            // Subtle perimeter strip so all 4 edges stay vivid for Ambilight sensors
            drawRect(
                brush = Brush.horizontalGradient(
                    0.0f to topLeft.copy(alpha = 0.30f),
                    0.12f to Color.Transparent,
                    0.88f to Color.Transparent,
                    1.0f to topRight.copy(alpha = 0.30f)
                )
            )
        }
        content()
    }
}

private fun extractQuadrantPalette(bitmap: Bitmap): AmbilightPalette {
    val w = bitmap.width
    val h = bitmap.height
    if (w < 2 || h < 2) return DefaultKidsAmbilightPalette

    return AmbilightPalette(
        topLeft = sampleRegionColor(bitmap, 0, 0, w / 2, h / 2),
        topRight = sampleRegionColor(bitmap, w / 2, 0, w, h / 2),
        bottomLeft = sampleRegionColor(bitmap, 0, h / 2, w / 2, h),
        bottomRight = sampleRegionColor(bitmap, w / 2, h / 2, w, h)
    )
}

private fun sampleRegionColor(
    bitmap: Bitmap,
    startX: Int,
    startY: Int,
    endX: Int,
    endY: Int
): Color {
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0L

    for (y in startY until endY step 2) {
        for (x in startX until endX step 2) {
            val pixel = bitmap.getPixel(x, y)
            rSum += AndroidColor.red(pixel)
            gSum += AndroidColor.green(pixel)
            bSum += AndroidColor.blue(pixel)
            count++
        }
    }

    if (count == 0L) return DefaultKidsAmbilightPalette.topLeft

    val r = (rSum / count).toInt()
    val g = (gSum / count).toInt()
    val b = (bSum / count).toInt()

    // Boost saturation and clamp brightness so Ambilight LEDs look vivid without washing out text
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(r, g, b, hsv)
    hsv[1] = (hsv[1] * 1.35f).coerceIn(0.45f, 0.95f)
    hsv[2] = hsv[2].coerceIn(0.42f, 0.85f)
    return Color(AndroidColor.HSVToColor(hsv))
}
