package com.redforge.intro

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.*
import kotlin.random.Random

private const val T0 = .3f
private const val LOOP = 8.2f
private const val G = 6000f
private const val Y0 = -950f
private const val REST = .3f
private val BG = Color(0xFF070403)

private fun clamp(x: Float, a: Float = 0f, b: Float = 1f) = min(b, max(a, x))
private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
private fun outBack(x: Float): Float { val c = 1.4f; return 1 + (c + 1) * (x - 1).pow(3) + c * (x - 1).pow(2) }
private fun easeOut(x: Float) = 1 - (1 - x).pow(3)

private class Hit(val t: Float, val s: Float)
private class Ember(var x: Float, var y: Float, val vx: Float, var vy: Float, val s: Float, var l: Float, val life: Float, val ph: Float)
private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var l: Float, var life: Float, val sz: Float)

private class Sim {
    val ys = ArrayList<Float>(); val vs = ArrayList<Float>(); val hits = ArrayList<Hit>()
    var tHit = 0f; var vMax = 1f
    var embers = ArrayList<Ember>(); var sparks = ArrayList<Spark>()
    var hitDone = 0; var last = 0f; var emAcc = 0f

    var y = 0f; var r = 0f; var sx = 1f; var sy = 1f; var sc = 1f; var anOp = 0f; var blur = 0f
    var flOn = false; var flY = 0f; var flSx = 1f; var flSy = 1f; var skew = 0f
    var eyeOn = false; var eyeSy = 1f
    var glowA = 0f; var glowScale = 1f; var haloOp = 0f; var fade = 1f
    var shakeX = 0f; var shakeY = 0f

    init {
        var yy = Y0; var v = 900f; var t = 0f; val dt = 1f / 240f
        while (t < 3f) {
            v += G * dt; yy += v * dt; t += dt
            if (yy >= 0f) {
                val sp = v; yy = 0f
                if (sp > 140f) { hits.add(Hit(T0 + t, sp)); v = -sp * REST } else v = 0f
            }
            ys.add(yy); vs.add(v)
        }
        tHit = hits[0].t; vMax = hits[0].s
    }

    private fun burst(s: Float) {
        val n = (8 + s / 140f).roundToInt()
        repeat(n) {
            val a = -PI.toFloat() / 2 + rnd(-1.45f, 1.45f); val v = rnd(.25f, 1f) * s * .32f
            sparks.add(Spark(512f + rnd(-150f, 150f), 902f, cos(a) * v * .7f, sin(a) * v * .9f, 0f, rnd(.45f, .95f), rnd(0f, 14f)))
        }
    }

    fun update(t: Float) {
        val dt = min(.05f, t - last); last = t
        val i = ((t - T0) * 240f).roundToInt().coerceIn(0, ys.size - 1)
        y = ys[i]; val v = vs[i]
        var rr = 0f; sy = 1f; sx = 1f; var shake = 0f
        if (t < tHit) { val p = clamp((t - T0) / (tHit - T0)); rr = -22f * (1 - p).pow(1.6f) }
        hits.forEachIndexed { k, h ->
            val d = t - h.t
            if (d >= 0) {
                val f = h.s / vMax; val sg = if (k % 2 == 1) 1f else -1f
                rr += sg * 9f * f * exp(-7f * d) * sin(26f * d)
                val q = exp(-16f * d) * f
                sy -= .09f * q; sx += .06f * q; shake = max(shake, f * exp(-9f * d))
            }
        }
        r = rr
        while (hitDone < hits.size && t >= hits[hitDone].t) { burst(hits[hitDone].s); hitDone++ }
        val depth = max(0f, -y) / 900f
        sc = 1 + depth * 1.6f + max(0f, -y) * .0004f
        anOp = clamp((t - T0 + .05f) * 8f)
        blur = min(7f, abs(v) / 650f) * (if (depth > .02f) 1f else 0f)

        val tf = T0 + 1.2f; val fp = clamp((t - tf) / 1.25f); val up = outBack(fp)
        flOn = t > tf
        val fs = .6f + .4f * easeOut(fp); flY = 520f * (1 - up)
        val fw = sin(t * 9f) * .018f + sin(t * 15.3f) * .012f
        flSx = fs * (1 - fw * .6f); flSy = fs * (1 + fw); skew = sin(t * 5.5f) * 1.6f
        val ep = clamp((t - tf - .95f) / .35f)
        eyeSy = .05f + .95f * outBack(ep); eyeOn = ep > 0f

        var flash = 0f
        hits.forEach { h -> if (t >= h.t) flash = max(flash, h.s / vMax * exp(-5f * (t - h.t))) }
        val heat = clamp((t - tf) / 1.6f) * (.8f + .12f * sin(t * 7f) + .08f * sin(t * 13.7f))
        val gl = clamp(max(flash * .9f, heat)); fade = clamp((LOOP - t) / 1f)
        glowA = gl * fade; glowScale = 1 + heat * .06f + flash * .08f
        haloOp = clamp((flash * .5f + heat * .7f) * fade)
        shakeX = sin(t * 95f) * shake * 14f; shakeY = cos(t * 80f) * shake * 18f

        emAcc += if (t > tf && t < LOOP - 1.2f) dt * (if (t < tf + 3f) 46f else 32f) else 0f
        while (emAcc > 1f) {
            emAcc--
            embers.add(Ember(512f + rnd(-190f, 190f) * (if (Random.nextFloat() < .5f) 1f else .55f), rnd(560f, 690f),
                rnd(-25f, 25f), -rnd(50f, 190f), rnd(6f, 20f), 0f, rnd(2f, 4.2f), rnd(0f, 6.3f)))
        }
        for (e in embers) {
            e.l += dt; e.x += (e.vx + sin(e.l * 2.4f + e.ph) * 35f) * dt; e.y += e.vy * dt; e.vy *= .995f
        }
        embers.removeAll { it.l >= it.life }
        for (p in sparks) {
            p.l += dt; p.vy += 2600f * dt; p.x += p.vx * dt; p.y += p.vy * dt
            if (p.y > 902f && p.vy > 0f) { p.y = 902f; p.vy *= -.3f; p.vx *= .6f; p.life = min(p.life, p.l + .12f) }
        }
        sparks.removeAll { it.l >= it.life }
    }
}

private class Shapes {
    private fun p(d: String) = PathParser().parsePathString(d).toPath()
    val flame0 = p("M532.222 117.464L532.94 117.768C533.296 121.125 524.681 136.13 523.185 139.949C508.078 178.511 511.437 215.57 538.349 247.853C551.804 263.993 559.361 276.635 581.15 282.698C590.738 280.495 595.876 273.722 600.344 265.466C609.921 247.769 619.982 238.684 635.464 226.087C633.034 232.329 631.909 239.69 631.729 246.434C630.39 296.369 679.405 327.494 694.277 371.975C700.076 389.319 702.089 406.574 702.01 424.74C701.992 429.047 701.983 433.74 703.665 437.773C713.68 455.131 720.614 426.217 722.556 419.092C738.002 445.365 746.533 487.068 743.304 517.266C740.309 545.262 730.871 571.557 715.271 594.966C712.108 599.711 707.938 606.444 703.634 609.948C697.334 610.914 688.068 611.208 681.544 611.668C654.336 613.584 626.909 612.608 599.618 612.523C547.554 612.625 495.49 612.457 443.427 612.018C425.377 611.405 402.485 609.438 388.556 623.261C386.829 624.975 385.001 627.156 383.397 628.994C367.245 628.926 350.661 629.28 334.601 628.957C323.058 620.089 306.675 597.381 299.611 584.572C274.821 538.637 273.146 483.701 295.09 436.341C314.162 395.125 358.587 364.285 353.567 315.433C352.451 304.567 347.363 296.206 342.11 286.938C379.341 288.646 403.382 303.737 411.358 342.012C412.939 349.594 413.644 357.442 416.074 364.786C419.718 378.005 436.995 380.578 444.627 369.412C463.386 341.965 434.168 314.289 423.501 291.58C413.776 270.876 418.335 243.207 426.053 223.409C444.505 176.077 486.755 137.938 532.222 117.464Z")
    val flame1 = p("M520.908 128.934C522.127 130.385 518.835 136.847 517.839 139.105C509.502 158.01 501.705 182.735 504.627 203.491C507.896 226.712 522.17 249.213 537.189 266.772C544.738 275.599 551.042 283.926 560.677 291.157C568.744 297.211 578.714 302.396 589.199 300.824C605.457 298.386 603.677 279.143 609.365 267.648C613.216 259.867 618.372 252.866 623.544 245.925C623.294 252.078 622.974 258.752 623.898 264.879C628.911 298.104 655.551 322.185 671.546 350.336C676.651 359.321 680.563 370.151 683.949 380.02C691.655 402.475 682.417 428.599 690.496 450.758C692.31 455.336 695.885 458.841 700.484 460.498C714.537 465.563 720.1 447.818 723.883 438.008C744.07 487.782 731.896 558.721 696.82 599.401C656.229 602.259 620.486 601.213 580.024 601.129L446.296 601.506C427.793 601.354 403.75 602.475 386.592 609.816C385.136 610.439 379.735 615.908 377.564 617.488C366.264 617.594 352.809 617.96 341.725 617.215C329.686 608.791 315.849 588.742 309.092 575.524C283.73 525.792 290.214 464.095 319.404 417.432C337.649 388.265 365.64 357.891 362.103 320.816C361.083 310.128 356.265 300.984 351.333 291.675C357.331 293.894 363.743 295.251 369.633 297.932C391.698 307.976 396.884 325.758 398.847 348.109C399.728 356.812 401.254 364.934 405.324 372.733C413.305 388.029 434.376 393.801 449.242 385.383C455.952 381.583 459.977 375.133 461.815 367.743C468.296 341.681 452.14 320.675 438.977 300.03C427.824 282.161 428.891 254.966 434.229 235.2C446.179 190.955 482.062 151.768 520.908 128.934Z")
    val eyeR = p("M640.74 475.453L641.745 475.854C642.387 479.052 637.883 482.733 635.664 485.443C627.036 495.982 627.004 509.196 620.766 521.535C614.89 533.155 607.781 541.707 595.17 546.158C581.743 550.896 571.384 544.383 559.608 538.564C551.826 540.443 548.484 541.5 541.125 537.778C535.561 533.756 533.484 529.822 531.452 523.554C533.421 524.05 535.407 524.472 537.406 524.821C550.541 527.061 558.73 518.35 568.818 511.251L572.025 514.952C593.147 500.562 616.662 484.039 640.74 475.453Z")
    val eyeL = p("M385.759 478.153C390.989 479.488 408.958 491.462 414.731 495.022L452.081 517.781L456.325 512.907C473.646 522.59 472.242 527.801 494.182 524.004C491.126 528.561 489.655 530.693 485.647 534.417C476.652 542.261 473.654 542.171 462.314 540.212C458.256 542.053 453.456 544.65 449.481 546.236C441.133 549.617 431.778 549.515 423.506 545.951C402.046 536.564 398.092 515.149 393.89 495.556C393.05 491.64 387.602 482.938 385.759 478.153Z")
    val anvil0 = p("M403.577 628.225C417.17 627.461 435.765 627.947 449.588 627.927L541.769 628.056L805.888 628.057C804.765 641.191 804.059 652.824 793.913 662.633C778.687 677.352 752.587 677.301 732.151 684.304C701.672 694.749 670.859 709.02 648.009 732.311C633.763 746.832 621.188 767.27 621.813 788.048C622.604 814.376 640.661 833.363 664.536 842.113C669.837 844.056 684.678 847.561 687.406 851.078C695.811 861.908 694.422 890.034 694.053 902.584C662.936 901.919 630.664 903.349 599.644 902.621C595.924 896.263 592.698 892.06 587.622 886.831C584.234 883.561 580.536 880.629 576.579 878.075C545.061 857.748 499.284 864.186 475.854 893.858C473.638 896.665 471.669 899.794 469.741 902.809C459.126 903.092 448.069 902.761 437.402 902.745L376.947 902.944C377.088 891.506 375.595 862.909 382.714 852.939C387.304 846.512 406.646 842.915 414.586 838.659C429.171 830.841 440.717 818.825 446.219 802.893C449.172 794.201 448.898 783.567 444.511 775.488C428.128 746.324 387.208 747.963 358.524 743.094C332.203 738.626 307.718 729.919 283.587 718.955C258.319 707.473 235.201 692.792 218.112 670.512C212.124 662.706 207.864 654.575 203.407 645.934C224.658 645.221 249.109 645.838 270.693 645.756L401.862 645.083C402.38 639.458 402.951 633.839 403.577 628.225Z")
    val anvil1 = p("M415.641 641.061C429.971 641.679 445.599 641.14 460.035 641.116L539.003 641.102L791.324 641.128C790.995 642.046 790.631 642.951 790.233 643.841C781.73 663.165 760.64 662.74 742.945 667.402C733.245 669.958 721.173 673.406 711.746 677.165C671.872 693.226 632.366 717.441 614.256 758.259C597.304 796.467 617.212 837.399 654.728 852.814C662.026 855.813 669.847 857.727 677.079 860.807L677.791 861.114C681.074 869.087 680.57 880.393 680.568 889.001C656.327 889.054 631.124 888.647 606.985 889.436C590.657 867.495 577.206 859.817 550.639 854.107C516.801 848.636 483.503 860.798 463.33 888.994L390.102 889.061C390.248 884.351 390.306 864.834 393.924 862.115C398.783 858.465 409.88 856.065 416.244 853.149C422.717 850.131 428.802 846.343 434.367 841.867C458.485 822.529 473.043 786.213 450.252 759.816C424.259 729.71 380.956 736.026 346.484 727.184C311.838 718.297 273.092 702.575 245.184 679.493C237.865 673.285 231.985 666.449 226.064 659.043C283.511 658.247 342.427 658.774 399.976 658.593C399.585 683.529 399.159 686.312 406.19 710.292C406.863 703.15 408.609 693.658 409.63 686.343L415.641 641.061Z")
    val g0 = listOf(Color(0xFFCD3900), Color(0xFFF35A00))
    val g1 = listOf(Color(0xFFFF6800), Color(0xFFFFA701))
}

private fun DrawScope.sprite(c: Offset, r: Float, a: Float) {
    if (r <= 0f || a <= 0f) return
    drawCircle(
        Brush.radialGradient(
            0f to Color(255, 240, 190, 255), .25f to Color(255, 170, 50, 217),
            .6f to Color(255, 70, 15, 64), 1f to Color(255, 40, 0, 0), center = c, radius = r
        ), radius = r, center = c, alpha = a.coerceIn(0f, 1f), blendMode = BlendMode.Plus
    )
}

private fun DrawScope.haloPath(p: Path, alpha: Float, radius: Float) {
    if (alpha <= 0.002f) return
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), 255, 58, 16)
        maskFilter = BlurMaskFilter(max(.5f, radius), BlurMaskFilter.Blur.NORMAL)
    }
    drawContext.canvas.nativeCanvas.drawPath(p.asAndroidPath(), paint)
}

private fun DrawScope.anvilT(s: Sim, block: DrawScope.() -> Unit) = withTransform({
    translate(512f, 902f + s.y)
    rotate(s.r, Offset.Zero)
    scale(s.sc * s.sx, s.sc * s.sy, Offset.Zero)
    translate(-504f, -902f)
}, block)

private fun DrawScope.flameT(s: Sim, block: DrawScope.() -> Unit) = withTransform({
    translate(512f, 620f + s.flY)
    scale(s.flSx, s.flSy, Offset.Zero)
    transform(Matrix().apply { this[0, 1] = tan(Math.toRadians(s.skew.toDouble())).toFloat() })
    translate(-512f, -620f)
}, block)

private fun DrawScope.anvilFill(sh: Shapes) {
    drawPath(sh.anvil0, Color(0xFF860619))
    drawPath(sh.anvil1, Color(0xFFDC1B1A))
}

@Composable
fun AnvilDropIntro(onFinished: () -> Unit) {
    val sim = remember { Sim() }
    val sh = remember { Shapes() }
    var tick by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current.density
    val finish by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        var start = -1L
        while (true) {
            val now = withFrameNanos { it }
            if (start < 0) start = now
            val t = (now - start) / 1e9f
            if (t > LOOP) { finish(); break }
            sim.update(t); tick++
        }
    }

    Box(Modifier.fillMaxSize().background(BG)) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            tick
            alpha = sim.fade
            translationX = sim.shakeX * density
            translationY = sim.shakeY * density
        }) {
            Canvas(Modifier.fillMaxSize()) {
                tick
                val S = min(size.width, size.height); val K = S / 1024f
                val ox = (size.width - S) / 2f; val oy = (size.height - S) / 2f
                withTransform({ translate(ox, oy); scale(sim.glowScale, sim.glowScale, Offset(S / 2, S / 2)) }) {
                    val R = 1.351f * S
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color(255, 120, 30, 191), .22f to Color(230, 50, 20, 97),
                            .42f to Color(120, 10, 10, 41), .62f to Color(120, 10, 10, 0), 1f to Color(120, 10, 10, 0),
                            center = Offset(.5f * S, .608f * S), radius = R
                        ), radius = .62f * R, center = Offset(.5f * S, .608f * S),
                        alpha = sim.glowA.coerceIn(0f, 1f), blendMode = BlendMode.Screen
                    )
                }
                withTransform({ translate(ox, oy); scale(K, K, Offset.Zero) }) {
                    for (e in sim.embers) {
                        val k = e.l / e.life
                        val a = clamp(1 - k) * (.65f + .35f * sin(e.l * 19f + e.ph)) * clamp(e.l * 4f)
                        sprite(Offset(e.x, e.y), e.s * (1 - k * .55f), max(0f, a))
                    }
                    if (sim.haloOp > .002f) {
                        flameT(sim) { if (sim.flOn) { haloPath(sh.flame0, sim.haloOp, 59f / sim.flSx) } }
                        anvilT(sim) { haloPath(sh.anvil0, sim.haloOp * sim.anOp, 59f / sim.sc) }
                    }
                    if (sim.flOn) clipRect(-3000f, -3000f, 4000f, 640f) {
                        flameT(sim) {
                            drawPath(sh.flame0, Brush.linearGradient(sh.g0, Offset(488f, 652f), Offset(572f, 125f)))
                            drawPath(sh.flame1, Brush.linearGradient(sh.g1, Offset(511f, 619f), Offset(520f, 130f)))
                            if (sim.eyeOn) withTransform({
                                translate(0f, 510f); scale(1f, sim.eyeSy, Offset.Zero); translate(0f, -510f)
                            }) {
                                drawPath(sh.eyeR, Color.Black); drawPath(sh.eyeL, Color.Black)
                            }
                        }
                    }
                }
            }
            Canvas(Modifier.fillMaxSize().graphicsLayer {
                tick
                alpha = sim.anOp
                val b = sim.blur * density
                renderEffect = if (b > .05f) BlurEffect(b, b, TileMode.Decal) else null
            }) {
                val S = min(size.width, size.height); val K = S / 1024f
                withTransform({ translate((size.width - S) / 2f, (size.height - S) / 2f); scale(K, K, Offset.Zero) }) {
                    anvilT(sim) { anvilFill(sh) }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                tick
                val S = min(size.width, size.height); val K = S / 1024f
                withTransform({ translate((size.width - S) / 2f, (size.height - S) / 2f); scale(K, K, Offset.Zero) }) {
                    for (p in sim.sparks) {
                        val k = clamp(p.l / p.life); val a = (1 - k).pow(1.6f)
                        val tx = p.x - p.vx * .024f; val ty = p.y - p.vy * .024f
                        val gC = (225 - 140 * k).toInt(); val bC = (130 - 110 * k).toInt()
                        drawLine(
                            Brush.linearGradient(
                                listOf(Color(255, gC, bC, 0), Color(255, gC, bC, (a * 255).toInt().coerceIn(0, 255))),
                                Offset(tx, ty), Offset(p.x + .01f, p.y + .01f)
                            ),
                            Offset(tx, ty), Offset(p.x, p.y), strokeWidth = 3f * (1 - k * .7f),
                            cap = StrokeCap.Round, blendMode = BlendMode.Plus
                        )
                        val r = (30f + p.sz) * (1 - k * .55f)
                        for (j in 0 until 4) {
                            val f = j / 4f
                            sprite(Offset(p.x - p.vx * .02f * j, p.y - p.vy * .02f * j), r * (1 - f * .4f), a * .55f * (1 - f))
                        }
                    }
                }
            }
        }
    }
}
