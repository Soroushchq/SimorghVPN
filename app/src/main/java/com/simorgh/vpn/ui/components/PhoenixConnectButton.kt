package com.simorgh.vpn.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.simorgh.vpn.R
import com.simorgh.vpn.core.VpnStatus

/**
 * The Simorgh connect button: a golden (or teal when connected) flame disc
 * with a pulsing aura ring while busy/connected.
 */
@Composable
fun PhoenixConnectButton(
    status: VpnStatus,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connected = status == VpnStatus.CONNECTED
    val busy = status == VpnStatus.CONNECTING || status == VpnStatus.STOPPING

    val transition = rememberInfiniteTransition(label = "phoenix")
    val pulse by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val ringColor = when {
        connected -> MaterialTheme.colorScheme.secondary
        busy -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(184.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
        ) {
            if (busy || connected) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f * pulse * 0.97f
                    drawCircle(
                        color = ringColor.copy(alpha = if (busy) 0.7f else 0.35f),
                        radius = r,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = if (connected)
                                listOf(
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.secondaryContainer
                                )
                            else
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                        ),
                        shape = CircleShape
                    )
            )
            Icon(
                painter = painterResource(R.drawable.ic_notification),
                contentDescription = null,
                tint = if (connected) MaterialTheme.colorScheme.onSecondary
                else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(64.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}
