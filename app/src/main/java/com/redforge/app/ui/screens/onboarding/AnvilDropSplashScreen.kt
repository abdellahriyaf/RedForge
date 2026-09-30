package com.redforge.app.ui.screens.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.core.graphics.PathParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val INTRO_SECONDS = 5.35f
private const val T0 = 0.30f
private const val G = 6000f
private const val Y0 = -950f
private const val GROUND = 0f
private const val RESTITUTION = 0.30f
private const val HIT_AFTER_T0 = 0.43238017f
private const val FIRST_HIT = T0 + HIT_AFTER_T0
private const val VMAX = 3494.281f

private data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val life: Float,
    val delay: Float
)

private val flameOuterData = "M532.222 117.464L532.94 117.768C533.296 121.125 524.681 136.13 523.185 139.949C508.078 178.511 511.437 215.57 538.349 247.853C551.804 263.993 559.361 276.635 581.15 282.698C590.738 280.495 595.876 273.722 600.344 265.466C609.921 247.769 619.982 238.684 635.464 226.087C633.034 232.329 631.909 239.69 631.729 246.434C630.39 296.369 679.405 327.494 694.277 371.975C700.076 389.319 702.089 406.574 702.01 424.74C701.992 429.047 701.983 433.74 703.665 437.773C713.68 455.131 720.614 426.217 722.556 419.092C738.002 445.365 746.533 487.068 743.304 517.266C740.309 545.262 730.871 571.557 715.271 594.966C712.108 599.711 707.938 606.444 703.634 609.948C697.334 610.914 688.068 611.208 681.544 611.668C654.336 613.584 626.909 612.608 599.618 612.523C547.554 612.625 495.49 612.457 443.427 612.018C425.377 611.405 402.485 609.438 388.556 623.261C386.829 624.975 385.001 627.156 383.397 628.994C367.245 628.926 350.661 629.28 334.601 628.957C323.058 620.089 306.675 597.381 299.611 584.572C274.821 538.637 273.146 483.701 295.09 436.341C314.162 395.125 358.587 364.285 353.567 315.433C352.451 304.567 347.363 296.206 342.11 286.938C379.341 288.646 403.382 303.737 411.358 342.012C412.939 349.594 413.644 357.442 416.074 364.786C419.718 378.005 436.995 380.578 444.627 369.412C463.386 341.965 434.168 314.289 423.501 291.58C413.776 270.876 418.335 243.207 426.053 223.409C444.505 176.077 486.755 137.938 532.222 117.464Z"
private val flameInnerData = "M520.908 128.934C522.127 130.385 518.835 136.847 517.839 139.105C509.502 158.01 501.705 182.735 504.627 203.491C507.896 226.712 522.17 249.213 537.189 266.772C544.738 275.599 551.042 283.926 560.677 291.157C568.744 297.211 578.714 302.396 589.199 300.824C605.457 298.386 603.677 279.143 609.365 267.648C613.216 259.867 618.372 252.866 623.544 245.925C623.294 252.078 622.974 258.752 623.898 264.879C628.911 298.104 655.551 322.185 671.546 350.336C676.651 359.321 680.563 370.151 683.949 380.02C691.655 402.475 682.417 428.599 690.496 450.758C692.31 455.336 695.885 458.841 700.484 460.498C714.537 465.563 720.1 447.818 723.883 438.008C744.07 487.782 731.896 558.721 696.82 599.401C656.229 602.259 620.486 601.213 580.024 601.129L446.296 601.506C427.793 601.354 403.75 602.475 386.592 609.816C385.136 610.439 379.735 615.908 377.564 617.488C366.264 617.594 352.809 617.96 341.725 617.215C329.686 608.791 315.849 588.742 309.092 575.524C283.73 525.792 290.214 464.095 319.404 417.432C337.649 388.265 365.64 357.891 362.103 320.816C361.083 310.128 356.265 300.984 351.333 291.675C357.331 293.894 363.743 295.251 369.633 297.932C391.698 307.976 396.884 325.758 398.847 348.109C399.728 356.812 401.254 364.934 405.324 372.733C413.305 388.029 434.376 393.801 449.242 385.383C455.952 381.583 459.977 375.133 461.815 367.743C468.296 341.681 452.14 320.675 438.977 300.03C427.824 282.161 428.891 254.966 434.229 235.2C446.179 190.955 482.062 151.768 520.908 128.934Z"
private val rightEyeData = "M640.74 475.453L641.745 475.854C642.387 479.052 637.883 482.733 635.664 485.443C627.036 495.982 627.004 509.196 620.766 521.535C614.89 533.155 607.781 541.707 595.17 546.158C581.743 550.896 571.384 544.383 559.608 538.564C551.826 540.443 548.484 541.5 541.125 537.778C535.561 533.756 533.484 529.822 531.452 523.554C533.421 524.05 535.407 524.472 537.406 524.821C550.541 527.061 558.73 518.35 568.818 511.251L572.025 514.952C593.147 500.562 616.662 484.039 640.74 475.453Z"
private val leftEyeData = "M385.759 478.153C390.989 479.488 408.958 491.462 414.731 495.022L452.081 517.781L456.325 512.907C473.646 522.59 472.242 527.801 494.182 524.004C491.126 528.561 489.655 530.693 485.647 534.417C476.652 542.261 473.654 542.171 462.314 540.212C458.256 542.053 453.456 544.65 449.481 546.236C441.133 549.617 431.778 549.515 423.506 545.951C402.046 536.564 398.092 515.149 393.89 495.556C393.05 491.64 387.602 482.938 385.759 478.153Z"
private val anvilBackData = "M403.577 628.225C417.17 627.461 435.765 627.947 449.588 627.927L541.769 628.056L805.888 628.057C804.765 641.191 804.059 652.824 793.913 662.633C778.687 677.352 752.587 677.301 732.151 684.304C701.672 694.749 670.859 709.02 648.009 732.311C633.763 746.832 621.188 767.27 621.813 788.048C622.604 814.376 640.661 833.363 664.536 842.113C669.837 844.056 684.678 847.561 687.406 851.078C695.811 861.908 694.422 890.034 694.053 902.584C662.936 901.919 630.664 903.349 599.644 902.621C595.924 896.263 592.698 892.06 587.622 886.831C584.234 883.561 580.536 880.629 576.579 878.075C545.061 857.748 499.284 864.186 475.854 893.858C473.638 896.665 471.669 899.794 469.741 902.809C459.126 903.092 448.069 902.761 437.402 902.745L376.947 902.944C377.088 891.506 375.595 862.909 382.714 852.939C387.304 846.512 406.646 842.915 414.586 838.659C429.171 830.841 440.717 818.825 446.219 802.893C449.172 794.201 448.898 783.567 444.511 775.488C428.128 746.324 387.208 747.963 358.524 743.094C332.203 738.626 307.718 729.919 283.587 718.955C258.319 707.473 235.201 692.792 218.112 670.512C212.124 662.706 207.864 654.575 203.407 645.934C224.658 645.221 249.109 645.838 270.693 645.756L401.862 645.083C402.38 639.458 402.951 633.839 403.577 628.225Z"
private val anvilFrontData = "M415.641 641.061C429.971 641.679 445.599 641.14 460.035 641.116L539.003 641.102L791.324 641.128C790.995 642.046 790.631 642.951 790.233 643.841C781.73 663.165 760.64 662.74 742.945 667.402C733.245 669.958 721.173 673.406 711.746 677.165C671.872 693.226 632.366 717.441 614.256 758.259C597.304 796.467 617.212 837.399 654.728 852.814C662.026 855.813 669.847 857.727 677.079 860.807L677.791 861.114C681.074 869.087 680.57 880.393 680.568 889.001C656.327 889.054 631.124 888.647 606.985 889.436C590.657 867.495 577.206 859.817 550.639 854.107C516.801 848.636 483.503 860.798 463.33 888.994L390.102 889.061C390.248 884.351 390.306 864.834 393.924 862.115C398.783 858.465 409.88 856.065 416.244 853.149C422.717 850.131 428.802 846.343 434.367 841.867C458.485 822.529 473.043 786.213 450.252 759.816C424.259 729.71 380.956 736.026 346.484 727.184C311.838 718.297 273.092 702.575 245.184 679.493C237.865 673.285 231.985 666.449 226.064 659.043C283.511 658.247 342.427 658.774 399.976 658.593C399.585 683.529 399.159 686.312 406.19 710.292C406.863 703.15 408.609 693.658 409.63 686.343L415.641 641.061Z"

private fun svgPath(data: String): Path =
    PathParser.createPathFromPathData(data).asComposePath()

private fun easeOut(x: Float): Float = 1f - (1f - x).let { it * it * it }
private fun clamp(x: Float, minValue: Float = 0f, maxValue: Float = 1f) =
    x.coerceIn(minValue, maxValue)

private fun outBack(x: Float): Float {
    val c = 1.4f
    return 1f + (c + 1f) * (x - 1f).let { it * it * it } + c * (x - 1f).let { it * it }
}

private fun preSimulatedAnvil(t: Float): Pair<Float, Float> {
    if (t < T0) return Y0 to 900f

    val flightTime = t - T0
    if (flightTime <= HIT_AFTER_T0) {
        val y = Y0 + 900f * flightTime + 0.5f * G * flightTime * flightTime
        val v = 900f + G * flightTime
        return y to v
    }

    val reboundTime = flightTime - HIT_AFTER_T0
    val reboundVelocity = -VMAX * RESTITUTION
    val reboundDuration = (-2f * reboundVelocity / G)
    if (reboundTime <= reboundDuration) {
        val y = reboundVelocity * reboundTime + 0.5f * G * reboundTime * reboundTime
        val v = reboundVelocity + G * reboundTime
        return y to v
    }

    return GROUND to 0f
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val flameOuter = remember { svgPath(flameOuterData) }
    val flameInner = remember { svgPath(flameInnerData) }
    val rightEye = remember { svgPath(rightEyeData) }
    val leftEye = remember { svgPath(leftEyeData) }
    val anvilBack = remember { svgPath(anvilBackData) }
    val anvilFront = remember { svgPath(anvilFrontData) }

    val particles = remember {
        val random = Random(0x5EED)
        List(58) {
            Particle(
                x = 512f + random.nextFloat() * 380f - 190f,
                y = 560f + random.nextFloat() * 150f,
                vx = random.nextFloat() * 50f - 25f,
                vy = -(50f + random.nextFloat() * 140f),
                size = 6f + random.nextFloat() * 14f,
                life = 1.8f + random.nextFloat() * 2.4f,
                delay = 1.50f + random.nextFloat() * 1.0f
            )
        }
    }

    var elapsed by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (isActive && elapsed < INTRO_SECONDS) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    elapsed += ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
                }
                lastNanos = now
            }
        }
        delay(40)
        onFinished()
    }

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        drawRect(Color(0xFF070403))
        val side = min(size.width, size.height)
        val unit = side / 1024f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f

        withTransform({
            translate(left, top)
            scale(unit, unit)
        }) {
            drawAnvilDrop(
                elapsed = elapsed,
                particles = particles,
                flameOuter = flameOuter,
                flameInner = flameInner,
                rightEye = rightEye,
                leftEye = leftEye,
                anvilBack = anvilBack,
                anvilFront = anvilFront
            )
        }
    }
}

private fun DrawScope.drawAnvilDrop(
    elapsed: Float,
    particles: List<Particle>,
    flameOuter: Path,
    flameInner: Path,
    rightEye: Path,
    leftEye: Path,
    anvilBack: Path,
    anvilFront: Path
) {
    val (y, velocity) = preSimulatedAnvil(elapsed)
    val impactTime = FIRST_HIT
    val impact = clamp((elapsed - impactTime) * 4.2f)
    val heatStart = 1.50f
    val heat = clamp((elapsed - heatStart) / 1.25f)
    val fade = clamp((INTRO_SECONDS - elapsed) / 0.75f)
    val preImpact = clamp((elapsed - T0) / (impactTime - T0))
    var rotation = if (elapsed < impactTime) -22f * (1f - preImpact).let { it * it } else 0f
    val shake = if (elapsed >= impactTime) exp(-8f * (elapsed - impactTime)) else 0f
    rotation += -9f * shake * sin((elapsed - impactTime) * 26f)

    val compression = 1f - 0.09f * exp(-16f * max(0f, elapsed - impactTime))
    val stretch = 1f + 0.06f * exp(-16f * max(0f, elapsed - impactTime))
    val depth = max(0f, -y) / 900f
    val scale = 1f + depth * 1.6f + max(0f, -y) * 0.0004f

    val glowAlpha = max(
        impact * exp(-5f * max(0f, elapsed - impactTime)),
        heat * (0.8f + 0.12f * sin(elapsed * 7f))
    ).coerceIn(0f, 1f) * fade

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xCCFF781E),
                Color(0x61E63214),
                Color(0x29140000),
                Color.Transparent
            ),
            center = Offset(512f, 575f),
            radius = 430f * (1f + heat * 0.06f)
        ),
        center = Offset(512f, 575f),
        radius = 430f * (1f + heat * 0.06f),
        alpha = glowAlpha,
        blendMode = BlendMode.Screen
    )

    particles.forEach { p ->
        val age = elapsed - p.delay
        if (age <= 0f || age >= p.life) return@forEach
        val k = age / p.life
        val alpha = (1f - k).coerceIn(0f, 1f) * (0.55f + 0.45f * sin(age * 19f))
        val px = p.x + (p.vx + sin(age * 2.4f + p.x) * 35f) * age
        val py = p.y + p.vy * age
        drawCircle(
            color = Color(0xFFFFB232),
            radius = p.size * (1f - k * 0.55f),
            center = Offset(px, py),
            alpha = alpha.coerceAtLeast(0f),
            blendMode = BlendMode.Screen
        )
    }

    withTransform({
        translate(512f, 902f + y)
        rotate(rotation)
        scale(scale * stretch, scale * compression)
        translate(-504f, -902f)
    }) {
        drawPath(
            anvilBack,
            color = Color(0xFF860619),
            alpha = clamp((elapsed - T0 + 0.05f) * 8f)
        )
        drawPath(
            anvilFront,
            color = Color(0xFFDC1B1A),
            alpha = clamp((elapsed - T0 + 0.05f) * 8f)
        )
    }

    val flameProgress = clamp((elapsed - heatStart) / 1.25f)
    val flameScale = 0.6f + 0.4f * easeOut(flameProgress)
    val flameY = 520f * (1f - outBack(flameProgress))
    val flameWobble = sin(elapsed * 9f) * 0.018f + sin(elapsed * 15.3f) * 0.012f

    if (elapsed > heatStart) {
        withTransform({
            translate(512f, 620f + flameY)
            scale(flameScale * (1f - flameWobble * 0.6f), flameScale * (1f + flameWobble))
            translate(-512f, -620f)
        }) {
            drawPath(
                flameOuter,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFCD3900), Color(0xFFF35A00)),
                    start = Offset(488f, 652f),
                    end = Offset(572f, 125f)
                ),
                alpha = heat
            )
            drawPath(
                flameInner,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF6800), Color(0xFFFFA701)),
                    start = Offset(511f, 619f),
                    end = Offset(520f, 130f)
                ),
                alpha = heat
            )

            val eyeProgress = clamp((elapsed - heatStart - 0.95f) / 0.35f)
            val eyeScale = 0.05f + 0.95f * outBack(eyeProgress)
            withTransform({
                translate(0f, 510f)
                scale(1f, eyeScale)
                translate(0f, -510f)
            }) {
                drawPath(rightEye, color = Color.Black, alpha = eyeProgress)
                drawPath(leftEye, color = Color.Black, alpha = eyeProgress)
            }
        }
    }

    if (impact > 0f) {
        val sparkAlpha = impact * exp(-5f * max(0f, elapsed - impactTime)) * fade
        repeat(12) { index ->
            val angle = -Math.PI.toFloat() / 2f + (index - 5.5f) * 0.19f
            val distance = 55f + 115f * impact + index * 4f
            val start = Offset(512f + cos(angle) * 35f, 902f + sin(angle) * 20f)
            val end = Offset(512f + cos(angle) * distance, 902f + sin(angle) * distance)
            drawLine(
                color = Color(0xFFFFB04A),
                start = start,
                end = end,
                strokeWidth = 2.5f * (1f - impact * 0.35f),
                alpha = sparkAlpha
            )
        }
    }
}
