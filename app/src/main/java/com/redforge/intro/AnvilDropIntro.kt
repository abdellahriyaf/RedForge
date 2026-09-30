package com.redforge.intro

import android.graphics.BlurMaskFilter
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.first
import kotlin.math.*
import kotlin.random.Random

/*
 * RedForge "Anvil Drop" launch intro  (v2 - new flame artwork)
 *
 * Source of truth: "RedForge - Anvil Drop.html" (timing, physics, particles) and
 * "vectorize-and-remove-background.svg" (new full, round flame + eyes, native vector paths).
 * The anvil paths are unchanged from the previous version.
 *
 * What changed vs. the previous AnvilDropIntro.kt
 *  - Flame/eyes: new vector artwork. The HTML places it as a raster; here the SVG paths are drawn
 *    natively through ASSET_* (the exact affine that maps the SVG onto the HTML image rectangles).
 *  - Eyes squash pivot is y=512 (was 510), as in the new HTML.
 *  - Exit is event-driven: plays until the eyes have opened (~2.8 s) + a short beat, then fades
 *    (0.45 s). The old version always ran an 8.2 s loop. Tap skips (0.25 s fade).
 *  - ready parameter: the intro never leaves before the app is ready, and never waits longer
 *    than it has to once the animation beat is done.
 *  - Reduced motion (system animator scale = 0): no animation, finishes as soon as ready.
 *  - Per-frame allocation removed: pooled particles, one pre-rendered glow sprite bitmap, cached
 *    brushes/paints/matrix, inline transform helpers. (The old file built a gradient Brush per
 *    ember/spark per frame - several hundred shader allocations every frame.)
 *  - Halo blur (BlurMaskFilter) is only used on API 28+ (unreliable/unblurred on 24-27);
 *    anvil motion blur only on API 31+ (RenderEffect). Both degrade gracefully.
 *
 * Usage (drop-in, same signature as before; ready is optional):
 *   AnvilDropIntro(ready = settingsLoaded) { navigateToApp() }
 *
 * Launch theme (no new dependency needed):
 *   API 31+: windowSplashScreenBackground=#070403, windowSplashScreenAnimatedIcon=@android:color/transparent
 *   <API 31: windowBackground=#070403. The first Compose frame is the same #070403, so the handoff is seamless.
 */

// ---------------------------------------------------------------- timing / physics (from the HTML)
private const val T0 = .3f                       // anvil enters
private const val G = 6000f
private const val Y0 = -950f
private const val REST = .3f                     // bounce restitution
private const val TF = T0 + 1.2f                 // flame starts rising
private const val REVEAL_END = TF + .95f + .35f  // eyes fully open (~2.8 s)
private const val EXIT_AT = REVEAL_END + .2f     // earliest exit start
private const val FADE_FULL = .45f
private const val FADE_SKIP = .25f
private const val PATH_N = 720                   // 3 s @ 240 Hz pre-simulation
private const val MAX_E = 256
private const val MAX_S = 96
private val BG = Color(0xFF070403)

// SVG(vectorize-and-remove-background.svg) -> 1024 stage space; derived from the HTML image rect
// (x=279.3 y=108.2 w=466.1 h=625.3) vs the SVG flame bounding box.
private const val AX = 108.19f
private const val AY = 13.31f
private const val ASX = .7907f
private const val ASY = .7841f

// ---------------------------------------------------------------- path data
private const val FLAME_OUTER = "M537.918 121.021C537.987 123.488 533.865 131.044 532.412 133.609C527.272 142.682 524.605 151.236 521.261 160.926C509.47 195.89 510.706 229.959 528.689 264.19C538.234 282.358 578.999 345.353 608.869 333.291C615.121 330.767 624.395 311.731 628.801 305.345C637.069 293.361 652.22 272.18 665.323 265.316L665.983 264.974C660.647 279.767 661.036 293.662 664.092 308.916C673.867 357.6 713.788 390.213 734.346 433.754C746.537 459.575 752.318 487.195 751.505 515.814C751.271 524.061 750.801 543.975 762.152 545.577C771.815 546.941 778.292 523.187 778.73 516.485C803.498 562.832 811.607 627.85 802.005 679.395C787.073 759.552 739.579 824.285 672.968 869.893C657.614 880.208 641.235 888.906 624.092 895.85C566.191 919.854 509.003 923.86 447.621 912.308C438.045 910.092 429.037 907.869 419.657 904.918C344.625 880.532 282.167 827.66 245.731 757.682C203.923 678.975 207.013 583.996 253.849 508.173C273.136 477.591 298.262 451.349 309.31 416.536C315.129 398.198 315.066 376.38 309.135 357.999C307.457 352.799 303.898 346.394 301.276 341.444C323.243 342.782 342.619 347.307 359.494 362.113C375.689 376.324 381.612 395.088 385.512 415.601C387.306 425.033 387.598 434.764 391.053 443.733C395.219 454.611 406.237 461.698 417.569 456.444C431.949 449.776 436.066 431.946 435.207 417.492C434.796 389.227 415.194 372.783 403.447 349.304C400.067 342.548 397.91 334.522 396.48 327.121C391.573 303.806 396.292 281.384 404.464 259.417C424.728 204.946 466.544 161.404 516.479 132.743C523.383 128.78 530.689 124.42 537.918 121.021Z"
private const val FLAME_INNER = "M530.496 128.604C530.614 128.898 530.732 129.192 530.85 129.486C528.386 133.562 524.718 140.444 522.797 144.497C512.309 166.625 503.065 195.552 504.068 220.132C504.546 230.919 506.5 241.59 509.873 251.848C520.69 283.875 546.203 316.475 570.609 339.747C580.317 348.592 593.421 358.306 607.339 357.164C627.463 355.512 625.887 339.585 629.747 325.569C634.24 309.257 645.952 291.519 656.896 278.63L656.76 279.185C644.093 332.171 681.985 367.661 708.35 408.016C722.993 430.429 735.244 461.204 735.613 488.124C735.679 494.43 735.569 500.737 735.285 507.037C734.424 523.257 732.001 541.537 740.815 556.103C745.73 564.226 756.662 569.27 764.872 562.652C775.002 554.484 777.648 540.645 779.782 528.581C825.205 654.821 764.25 796.871 651.876 864.941C602.467 894.871 545.563 907.87 487.969 904.027C479.448 903.458 470.862 902.094 462.378 901.024C457.852 900.305 452.666 899.22 448.179 898.19C376.748 881.333 314.705 837.29 275.231 775.416C263.992 757.424 254.95 740.783 247.722 720.699C225.901 660.659 227.947 594.534 253.44 535.958C264.291 510.977 278.052 489.941 293.345 467.585C319.571 429.247 333.558 386.798 306.712 344.218C340.232 345.445 367.351 372.049 369.451 404.725C370.384 419.24 371.002 434.752 377.733 447.85C382.609 457.338 392.029 465.738 402.468 468.621C435.086 477.176 452.822 453.031 449.16 422.272C445.885 394.76 431.56 381.165 418.96 358.82C403.935 332.174 404.608 297.127 413.322 268.625C429.696 211.301 478.783 157.056 530.496 128.604Z"
private const val EYE_A = "M673.599 591.469L675.329 591.773L675.803 592.69C674.99 595.165 662.184 609.417 660.122 613.994C656.792 621.74 655.214 630.236 652.769 638.445C643.981 667.951 616.415 693.585 584.624 678.927C579.743 676.677 574.752 673.341 569.547 671.696C559.105 674.009 556 677.357 546.763 669.897C540.001 664.214 538.077 660.858 535.25 652.501C537.144 653.123 539.046 653.725 540.953 654.305C560.587 660.193 568.148 644.571 582.992 637.021L586.565 642.151C593.169 638.231 601.296 631.445 608.028 627.118C627.283 614.743 652.232 598.931 673.599 591.469Z"
private const val EYE_B = "M350.986 594.399C352.417 594.454 353.385 594.555 354.635 595.337C363.666 600.984 372.779 606.839 381.767 612.523C399.342 623.614 417.045 634.501 434.874 645.18C436.948 643.168 439.002 641.345 441.161 639.429C450.282 644.375 461.421 655.02 472.125 655.685C474.252 655.817 482.173 654.222 485.779 654.169C488.858 656.973 476.347 666.804 474.175 668.606C462.936 677.396 461.481 677 448.091 673.657C440.027 676.148 433.373 681.567 424.759 683.433C393.64 690.178 370.534 662.224 365.424 634.252C364.351 628.382 363.378 621.479 361.707 615.863C359.667 609.008 352.587 601.235 350.986 594.399Z"
private const val ANVIL_DARK = "M403.577 628.225C417.17 627.461 435.765 627.947 449.588 627.927L541.769 628.056L805.888 628.057C804.765 641.191 804.059 652.824 793.913 662.633C778.687 677.352 752.587 677.301 732.151 684.304C701.672 694.749 670.859 709.02 648.009 732.311C633.763 746.832 621.188 767.27 621.813 788.048C622.604 814.376 640.661 833.363 664.536 842.113C669.837 844.056 684.678 847.561 687.406 851.078C695.811 861.908 694.422 890.034 694.053 902.584C662.936 901.919 630.664 903.349 599.644 902.621C595.924 896.263 592.698 892.06 587.622 886.831C584.234 883.561 580.536 880.629 576.579 878.075C545.061 857.748 499.284 864.186 475.854 893.858C473.638 896.665 471.669 899.794 469.741 902.809C459.126 903.092 448.069 902.761 437.402 902.745L376.947 902.944C377.088 891.506 375.595 862.909 382.714 852.939C387.304 846.512 406.646 842.915 414.586 838.659C429.171 830.841 440.717 818.825 446.219 802.893C449.172 794.201 448.898 783.567 444.511 775.488C428.128 746.324 387.208 747.963 358.524 743.094C332.203 738.626 307.718 729.919 283.587 718.955C258.319 707.473 235.201 692.792 218.112 670.512C212.124 662.706 207.864 654.575 203.407 645.934C224.658 645.221 249.109 645.838 270.693 645.756L401.862 645.083C402.38 639.458 402.951 633.839 403.577 628.225Z"
private const val ANVIL_LIGHT = "M415.641 641.061C429.971 641.679 445.599 641.14 460.035 641.116L539.003 641.102L791.324 641.128C790.995 642.046 790.631 642.951 790.233 643.841C781.73 663.165 760.64 662.74 742.945 667.402C733.245 669.958 721.173 673.406 711.746 677.165C671.872 693.226 632.366 717.441 614.256 758.259C597.304 796.467 617.212 837.399 654.728 852.814C662.026 855.813 669.847 857.727 677.079 860.807L677.791 861.114C681.074 869.087 680.57 880.393 680.568 889.001C656.327 889.054 631.124 888.647 606.985 889.436C590.657 867.495 577.206 859.817 550.639 854.107C516.801 848.636 483.503 860.798 463.33 888.994L390.102 889.061C390.248 884.351 390.306 864.834 393.924 862.115C398.783 858.465 409.88 856.065 416.244 853.149C422.717 850.131 428.802 846.343 434.367 841.867C458.485 822.529 473.043 786.213 450.252 759.816C424.259 729.71 380.956 736.026 346.484 727.184C311.838 718.297 273.092 702.575 245.184 679.493C237.865 673.285 231.985 666.449 226.064 659.043C283.511 658.247 342.427 658.774 399.976 658.593C399.585 683.529 399.159 686.312 406.19 710.292C406.863 703.15 408.609 693.658 409.63 686.343L415.641 641.061Z"

private fun clamp(x: Float, a: Float = 0f, b: Float = 1f) = min(b, max(a, x))
private fun outBack(x: Float): Float { val c = 1.4f; return 1 + (c + 1) * (x - 1).pow(3) + c * (x - 1).pow(2) }
private fun easeOut(x: Float) = 1 - (1 - x).pow(3)

// ---------------------------------------------------------------- simulation (no allocation per frame)
private class Sim {
    private val rnd = Random(0x5EED)
    private fun rf(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)

    private val ys = FloatArray(PATH_N)
    private val vs = FloatArray(PATH_N)
    private val hitT = FloatArray(8)
    private val hitS = FloatArray(8)
    private var nHits = 0
    private var tHit = 0f
    private var vMax = 1f
    private var hitDone = 0
    private var last = 0f
    private var emAcc = 0f

    // embers (pooled)
    val ex = FloatArray(MAX_E); val ey = FloatArray(MAX_E); val evx = FloatArray(MAX_E); val evy = FloatArray(MAX_E)
    val es = FloatArray(MAX_E); val el = FloatArray(MAX_E); val elife = FloatArray(MAX_E); val eph = FloatArray(MAX_E)
    var ne = 0
    // sparks (pooled)
    val px = FloatArray(MAX_S); val py = FloatArray(MAX_S); val pvx = FloatArray(MAX_S); val pvy = FloatArray(MAX_S)
    val pl = FloatArray(MAX_S); val plife = FloatArray(MAX_S); val psz = FloatArray(MAX_S)
    var np = 0

    // pose
    var y = 0f; var r = 0f; var sx = 1f; var sy = 1f; var sc = 1f; var anOp = 0f; var blur = 0f
    var flOn = false; var flY = 0f; var flSx = 1f; var flSy = 1f
    val skewM = Matrix()
    var eyeOn = false; var eyeSy = 1f
    var glowA = 0f; var glowScale = 1f; var haloOp = 0f; var fade = 1f
    var shakeX = 0f; var shakeY = 0f

    var exitStart = -1f
    private var fadeDur = FADE_FULL

    init {
        var yy = Y0; var v = 900f; val dt = 1f / 240f
        for (i in 0 until PATH_N) {
            v += G * dt; yy += v * dt
            if (yy >= 0f) {
                val sp = v; yy = 0f
                if (sp > 140f) {
                    if (nHits < 8) { hitT[nHits] = T0 + (i + 1) * dt; hitS[nHits] = sp; nHits++ }
                    v = -sp * REST
                } else v = 0f
            }
            ys[i] = yy; vs[i] = v
        }
        tHit = hitT[0]; vMax = hitS[0]
    }

    fun beginExit(t: Float, dur: Float) { exitStart = t; fadeDur = dur }
    fun finished(t: Float) = exitStart >= 0f && t >= exitStart + fadeDur

    private fun burst(s: Float) {
        val n = (8 + s / 140f).roundToInt()
        for (k in 0 until n) {
            if (np >= MAX_S) return
            val a = -PI.toFloat() / 2 + rf(-1.45f, 1.45f); val v = rf(.25f, 1f) * s * .32f
            px[np] = 512f + rf(-150f, 150f); py[np] = 902f
            pvx[np] = cos(a) * v * .7f; pvy[np] = sin(a) * v * .9f
            pl[np] = 0f; plife[np] = rf(.45f, .95f); psz[np] = rf(0f, 14f); np++
        }
    }

    fun update(t: Float) {
        val dt = min(.05f, max(0f, t - last)); last = t
        val i = ((t - T0) * 240f).roundToInt().coerceIn(0, PATH_N - 1)
        y = ys[i]; val v = vs[i]
        var rr = 0f; sy = 1f; sx = 1f
        var shake = 0f; var flash = 0f
        if (t < tHit) { val p = clamp((t - T0) / (tHit - T0)); rr = -22f * (1 - p).pow(1.6f) }
        for (k in 0 until nHits) {
            val d = t - hitT[k]
            if (d >= 0f) {
                val f = hitS[k] / vMax; val sg = if (k % 2 == 1) 1f else -1f
                rr += sg * 9f * f * exp(-7f * d) * sin(26f * d)
                val q = exp(-16f * d) * f
                sy -= .09f * q; sx += .06f * q
                shake = max(shake, f * exp(-9f * d)); flash = max(flash, f * exp(-5f * d))
            }
        }
        r = rr
        while (hitDone < nHits && t >= hitT[hitDone]) { burst(hitS[hitDone]); hitDone++ }
        val depth = max(0f, -y) / 900f
        sc = 1 + depth * 1.6f + max(0f, -y) * .0004f
        anOp = clamp((t - T0 + .05f) * 8f)
        blur = min(7f, abs(v) / 650f) * (if (depth > .02f) 1f else 0f)

        val fp = clamp((t - TF) / 1.25f); val up = outBack(fp)
        flOn = t > TF
        val fs = .6f + .4f * easeOut(fp); flY = 520f * (1 - up)
        val fw = sin(t * 9f) * .018f + sin(t * 15.3f) * .012f
        flSx = fs * (1 - fw * .6f); flSy = fs * (1 + fw)
        skewM[0, 1] = tan(Math.toRadians((sin(t * 5.5f) * 1.6f).toDouble())).toFloat()
        val ep = clamp((t - TF - .95f) / .35f)
        eyeSy = .05f + .95f * outBack(ep); eyeOn = ep > 0f

        fade = if (exitStart < 0f) 1f else if (fadeDur <= 0f) 0f else 1f - clamp((t - exitStart) / fadeDur)
        val heat = clamp((t - TF) / 1.6f) * (.8f + .12f * sin(t * 7f) + .08f * sin(t * 13.7f))
        glowA = clamp(max(flash * .9f, heat)); glowScale = 1 + heat * .06f + flash * .08f
        haloOp = clamp(flash * .5f + heat * .7f)
        shakeX = sin(t * 95f) * shake * 14f; shakeY = cos(t * 80f) * shake * 18f

        // embers
        emAcc += if (t > TF && exitStart < 0f) dt * (if (t < TF + 3f) 46f else 32f) else 0f
        while (emAcc > 1f) {
            emAcc -= 1f
            if (ne < MAX_E) {
                ex[ne] = 512f + rf(-190f, 190f) * (if (rnd.nextFloat() < .5f) 1f else .55f); ey[ne] = rf(560f, 690f)
                evx[ne] = rf(-25f, 25f); evy[ne] = -rf(50f, 190f); es[ne] = rf(6f, 20f)
                el[ne] = 0f; elife[ne] = rf(2f, 4.2f); eph[ne] = rf(0f, 6.3f); ne++
            }
        }
        var e = 0
        while (e < ne) {
            el[e] += dt
            if (el[e] >= elife[e]) { // swap-remove
                val L = ne - 1
                ex[e] = ex[L]; ey[e] = ey[L]; evx[e] = evx[L]; evy[e] = evy[L]
                es[e] = es[L]; el[e] = el[L]; elife[e] = elife[L]; eph[e] = eph[L]; ne--
                continue
            }
            ex[e] += (evx[e] + sin(el[e] * 2.4f + eph[e]) * 35f) * dt; ey[e] += evy[e] * dt; evy[e] *= .995f
            e++
        }
        // sparks
        var s = 0
        while (s < np) {
            pl[s] += dt
            pvy[s] += 2600f * dt; px[s] += pvx[s] * dt; py[s] += pvy[s] * dt
            if (py[s] > 902f && pvy[s] > 0f) { py[s] = 902f; pvy[s] *= -.3f; pvx[s] *= .6f; plife[s] = min(plife[s], pl[s] + .12f) }
            if (pl[s] >= plife[s]) {
                val L = np - 1
                px[s] = px[L]; py[s] = py[L]; pvx[s] = pvx[L]; pvy[s] = pvy[L]
                pl[s] = pl[L]; plife[s] = plife[L]; psz[s] = psz[L]; np--
                continue
            }
            s++
        }
    }
}

// ---------------------------------------------------------------- shapes / cached draw resources
private class Shapes {
    private fun p(d: String) = PathParser().parsePathString(d).toPath()
    val flameOuter = p(FLAME_OUTER)
    val flameInner = p(FLAME_INNER)
    val eyeA = p(EYE_A)
    val eyeB = p(EYE_B)
    val anvilDark = p(ANVIL_DARK)
    val anvilLight = p(ANVIL_LIGHT)
    val flameOuterA = flameOuter.asAndroidPath()
    val anvilDarkA = anvilDark.asAndroidPath()

    // gradients from the SVG (userSpaceOnUse, bottom -> top)
    val gOuter = Brush.linearGradient(
        listOf(Color(0xFFCC1E00), Color(0xFFF45203)), Offset(518.3667f, 917.17554f), Offset(497.36789f, 123.52214f))
    val gInner = Brush.linearGradient(
        listOf(Color(0xFFFD3200), Color(0xFFFFB801)), Offset(521.32434f, 903.50494f), Offset(499.4595f, 130.82176f))
    // radial glow, design space (CSS: circle at 50% 56% of a 180% box => centre (512, 622.6), R = 1.351 * 1024)
    val glow = Brush.radialGradient(
        0f to Color(255, 120, 30, 191), .22f to Color(230, 50, 20, 97),
        .42f to Color(120, 10, 10, 41), .62f to Color(120, 10, 10, 0), 1f to Color(120, 10, 10, 0),
        center = Offset(512f, 622.6f), radius = 1383.4f)
}

private class Sprite {
    val img: ImageBitmap
    val paint = Paint().apply { blendMode = BlendMode.Plus; filterQuality = FilterQuality.Low }
    init {
        val bmp = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val ap = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        ap.shader = android.graphics.RadialGradient(
            32f, 32f, 32f,
            intArrayOf(
                android.graphics.Color.argb(255, 255, 240, 190), android.graphics.Color.argb(217, 255, 170, 50),
                android.graphics.Color.argb(64, 255, 70, 15), android.graphics.Color.argb(0, 255, 40, 0)),
            floatArrayOf(0f, .25f, .6f, 1f), android.graphics.Shader.TileMode.CLAMP)
        c.drawCircle(32f, 32f, 32f, ap)
        img = bmp.asImageBitmap()
    }
}

private class Halo {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var rad = -1f
    fun draw(cv: android.graphics.Canvas, path: android.graphics.Path, alpha: Float, radius: Float) {
        val q = (radius * 2f).roundToInt() / 2f
        if (q != rad) { rad = q; paint.maskFilter = BlurMaskFilter(max(.5f, q), BlurMaskFilter.Blur.NORMAL) }
        paint.color = android.graphics.Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), 255, 58, 16)
        cv.drawPath(path, paint)
    }
}

private fun DrawScope.sprite(sp: Sprite, x: Float, y: Float, r: Float, a: Float) {
    if (r <= 0f || a <= .002f) return
    drawIntoCanvas { c ->
        sp.paint.alpha = if (a > 1f) 1f else a
        c.save(); c.translate(x - r, y - r); c.scale(r / 32f, r / 32f)
        c.drawImage(sp.img, Offset.Zero, sp.paint)
        c.restore()
    }
}

private inline fun DrawScope.stage(ox: Float, oy: Float, k: Float, block: DrawScope.() -> Unit) =
    withTransform({ translate(ox, oy); scale(k, k, Offset.Zero) }, block)

private inline fun DrawScope.anvilT(s: Sim, block: DrawScope.() -> Unit) = withTransform({
    translate(512f, 902f + s.y)
    rotate(s.r, Offset.Zero)
    scale(s.sc * s.sx, s.sc * s.sy, Offset.Zero)
    translate(-504f, -902f)
}, block)

private inline fun DrawScope.flameT(s: Sim, block: DrawScope.() -> Unit) = withTransform({
    translate(512f, 620f + s.flY)
    scale(s.flSx, s.flSy, Offset.Zero)
    transform(s.skewM)
    translate(-512f, -620f)
}, block)

private inline fun DrawScope.assetT(block: DrawScope.() -> Unit) =
    withTransform({ translate(AX, AY); scale(ASX, ASY, Offset.Zero) }, block)

// ---------------------------------------------------------------- composable
/**
 * @param ready  true once the app can show its first real screen. The intro will not finish before
 *               this is true, and finishes right after the animation beat once it is.
 * @param onFinished called exactly once, after the exit fade, when the app UI should take over.
 */
@Composable
fun AnvilDropIntro(ready: Boolean = true, onFinished: () -> Unit) {
    val sim = remember { Sim() }
    val sh = remember { Shapes() }
    val spr = remember { Sprite() }
    val haloF = remember { Halo() }
    val haloA = remember { Halo() }
    val frame = remember { mutableIntStateOf(0) }
    val skip = remember { booleanArrayOf(false) }
    val density = LocalDensity.current.density
    val finish by rememberUpdatedState(onFinished)
    val readyState = rememberUpdatedState(ready)
    val ctx = LocalContext.current
    val reduceMotion = remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            snapshotFlow { readyState.value }.first { it }
            finish(); return@LaunchedEffect
        }
        var start = -1L
        var done = false
        while (true) {
            withFrameNanos { now ->
                if (start < 0L) start = now
                val t = (now - start) / 1e9f
                if (sim.exitStart < 0f && readyState.value && (t >= EXIT_AT || skip[0]))
                    sim.beginExit(t, if (skip[0]) FADE_SKIP else FADE_FULL)
                sim.update(t)
                frame.intValue++
                done = sim.finished(t)
            }
            if (done) { finish(); break }
        }
    }

    Box(Modifier.fillMaxSize().background(BG).pointerInput(Unit) { detectTapGestures { skip[0] = true } }) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            frame.intValue // observe
            alpha = sim.fade
            translationX = sim.shakeX * density; translationY = sim.shakeY * density
        }) {
            // glow, embers, halo, flame + eyes
            Canvas(Modifier.fillMaxSize()) {
                frame.intValue // observe
                val s = min(size.width, size.height); val k = s / 1024f
                val ox = (size.width - s) / 2f; val oy = (size.height - s) / 2f
                stage(ox, oy, k) {
                    withTransform({ scale(sim.glowScale, sim.glowScale, Offset(512f, 512f)) }) {
                        drawCircle(sh.glow, radius = 870f, center = Offset(512f, 622.6f),
                            alpha = sim.glowA.coerceIn(0f, 1f), blendMode = BlendMode.Screen)
                    }
                    for (e in 0 until sim.ne) {
                        val kk = sim.el[e] / sim.elife[e]
                        val a = clamp(1 - kk) * (.65f + .35f * sin(sim.el[e] * 19f + sim.eph[e])) * clamp(sim.el[e] * 4f)
                        sprite(spr, sim.ex[e], sim.ey[e], sim.es[e] * (1 - kk * .55f), a)
                    }
                    if (Build.VERSION.SDK_INT >= 28 && sim.haloOp > .002f) {
                        val cv = drawContext.canvas.nativeCanvas
                        if (sim.flOn) flameT(sim) { assetT { haloF.draw(cv, sh.flameOuterA, sim.haloOp, 59f / (sim.flSx * ASX)) } }
                        anvilT(sim) { haloA.draw(cv, sh.anvilDarkA, sim.haloOp * sim.anOp, 59f / sim.sc) }
                    }
                    if (sim.flOn) clipRect(-3000f, -3000f, 4000f, 640f) { // hidden behind the anvil top
                        flameT(sim) {
                            assetT {
                                drawPath(sh.flameOuter, sh.gOuter)
                                drawPath(sh.flameInner, sh.gInner)
                            }
                            if (sim.eyeOn) withTransform({
                                translate(0f, 512f); scale(1f, sim.eyeSy, Offset.Zero); translate(0f, -512f)
                            }) {
                                assetT { drawPath(sh.eyeA, Color.Black); drawPath(sh.eyeB, Color.Black) }
                            }
                        }
                    }
                }
            }
            // anvil (own layer: opacity + motion blur)
            Canvas(Modifier.fillMaxSize().graphicsLayer {
                frame.intValue // observe
                alpha = sim.anOp
                if (Build.VERSION.SDK_INT >= 31) {
                    val b = sim.blur * density
                    renderEffect = if (b > .05f) BlurEffect(b, b, TileMode.Decal) else null
                }
            }) {
                val s = min(size.width, size.height)
                stage((size.width - s) / 2f, (size.height - s) / 2f, s / 1024f) {
                    anvilT(sim) {
                        drawPath(sh.anvilDark, Color(0xFF860619))
                        drawPath(sh.anvilLight, Color(0xFFDC1B1A))
                    }
                }
            }
            // sparks
            Canvas(Modifier.fillMaxSize()) {
                frame.intValue // observe
                val s = min(size.width, size.height)
                stage((size.width - s) / 2f, (size.height - s) / 2f, s / 1024f) {
                    for (i in 0 until sim.np) {
                        val k = clamp(sim.pl[i] / sim.plife[i]); val a = (1 - k).pow(1.6f)
                        val x = sim.px[i]; val y = sim.py[i]; val vx = sim.pvx[i]; val vy = sim.pvy[i]
                        val tx = x - vx * .024f; val ty = y - vy * .024f
                        val g = (225 - 140 * k).toInt(); val b = (130 - 110 * k).toInt()
                        val w = 3f * (1 - k * .7f)
                        // tail -> head streak in 3 steps (a per-spark gradient shader would allocate every frame)
                        for (j in 0 until 3) {
                            val f0 = j / 3f; val f1 = (j + 1) / 3f
                            drawLine(Color(255, g, b, (a * 255 * f1).toInt().coerceIn(0, 255)),
                                Offset(tx + (x - tx) * f0, ty + (y - ty) * f0), Offset(tx + (x - tx) * f1, ty + (y - ty) * f1),
                                strokeWidth = w, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
                        }
                        val r = (30f + sim.psz[i]) * (1 - k * .55f)
                        for (j in 0 until 4) {
                            val f = j / 4f
                            sprite(spr, x - vx * .02f * j, y - vy * .02f * j, r * (1 - f * .4f), a * .55f * (1 - f))
                        }
                    }
                }
            }
        }
    }
}