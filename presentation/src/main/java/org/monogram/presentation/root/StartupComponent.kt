package org.monogram.presentation.root

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import org.monogram.domain.repository.ConnectionStatus
import org.monogram.presentation.R

interface StartupComponent {
    val connectionStatus: StateFlow<ConnectionStatus>
}

class DefaultStartupComponent(
    context: AppComponentContext
) : StartupComponent, AppComponentContext by context {
    override val connectionStatus: StateFlow<ConnectionStatus>
        get() = container.repositories.chatListRepository.connectionStateFlow
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StartupContent(
    component: StartupComponent,
    modifier: Modifier = Modifier,
    animateIn: Boolean = true,
    logoSize: Dp = 72.dp
) {
    val connectionStatus by component.connectionStatus.collectAsState()
    val showLogo = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    var revealDynamicElements by remember(animateIn) { mutableStateOf(!animateIn) }

    LaunchedEffect(animateIn) {
        if (animateIn) {
            delay(120)
            revealDynamicElements = true
        }
    }

    val startupAlpha by animateFloatAsState(
        targetValue = if (revealDynamicElements) 1f else 0.92f,
        animationSpec = tween(durationMillis = 260),
        label = "StartupAlpha"
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(horizontal = 24.dp)
                    .alpha(startupAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (showLogo) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = null,
                        modifier = Modifier.size(logoSize),
                        contentScale = ContentScale.Fit
                    )
                }

                AnimatedVisibility(
                    visible = revealDynamicElements,
                    enter = fadeIn(tween(260)) + slideInVertically(
                        animationSpec = tween(260),
                        initialOffsetY = { it / 5 }
                    ),
                    exit = fadeOut(tween(120))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(if (showLogo) 20.dp else 0.dp))

                        Text(
                            text = stringResource(R.string.app_name_monogram),
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        AnimatedContent(
                            targetState = connectionStatus,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "StartupStatus"
                        ) { status ->
                            Text(
                                text = when (status) {
                                    ConnectionStatus.WaitingForNetwork -> stringResource(R.string.waiting_for_network)
                                    ConnectionStatus.Connecting -> stringResource(R.string.connecting)
                                    ConnectionStatus.Updating -> stringResource(R.string.updating)
                                    ConnectionStatus.ConnectingToProxy -> stringResource(R.string.connecting_to_proxy)
                                    ConnectionStatus.Connected -> stringResource(R.string.startup_connecting)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        LinearWavyProgressIndicator(
                            modifier = Modifier.width(220.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}