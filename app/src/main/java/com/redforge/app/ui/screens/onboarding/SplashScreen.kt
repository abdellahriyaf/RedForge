package com.redforge.app.ui.screens.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.redforge.app.R
import com.redforge.app.ui.theme.ForgeGold
import com.redforge.app.ui.theme.ForgeGradientEnd
import com.redforge.app.ui.theme.ForgeGradientStart
import com.redforge.app.ui.theme.ForgeGlowGradient
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * First screen shown on every cold start. Purely presentational: shows the
 * brand mark with a scale-in animation plus a random motivational quote,
 * then hands off to whichever destination [onFinished] resolves to
 * (privacy policy, tutorial, or straight to Home) after a short beat.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val quotes = stringArrayResource(R.array.motivational_quotes)
    val quote = remember { quotes[Random.nextInt(quotes.size)] }

    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(200)
        alpha.animateTo(1f, animationSpec = tween(500))
        delay(1500)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ForgeGradientStart, ForgeGradientEnd))),
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .background(ForgeGlowGradient)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(84.dp)
                        .scale(scale.value)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "REDFORGE",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.scale(scale.value)
                )
                Text(
                    stringResource(R.string.tagline),
                    style = MaterialTheme.typography.labelLarge,
                    color = ForgeGold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(48.dp))
                Text(
                    "“$quote”",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(horizontal = 40.dp)
                        .alpha(alpha.value)
                )
            }
        }
    }
}
