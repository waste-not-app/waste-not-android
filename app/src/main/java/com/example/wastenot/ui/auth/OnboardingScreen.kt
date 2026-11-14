package com.example.wastenot.ui.auth

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.theme.*
import com.example.wastenot.ui.components.*
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val tag: Int,
    val title: Int,
    val body: Int,
)

private val onboardingPages = listOf(
    OnboardingPage(R.string.onboarding_slide1_tag, R.string.onboarding_slide1_title, R.string.onboarding_slide1_body),
    OnboardingPage(R.string.onboarding_slide2_tag, R.string.onboarding_slide2_title, R.string.onboarding_slide2_body),
    OnboardingPage(R.string.onboarding_slide3_tag, R.string.onboarding_slide3_title, R.string.onboarding_slide3_body),
)

@Composable
internal fun OnboardingScreen(
    onSkip: () -> Unit,
    onStartRegister: () -> Unit,
    onStartLogin: () -> Unit,
    onTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()

    Box(modifier.ambientCanvasBackground(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandWordmark()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextActionButton(stringResource(R.string.onboarding_skip), onSkip)
                    IconActionButton(R.drawable.ic_device, stringResource(R.string.theme_title), onTheme, circle = true)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("onboarding-pager"),
            ) { pageIndex ->
                val page = onboardingPages[pageIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                ) {
                    when (pageIndex) {
                        0 -> SlideTrackingIllustration()
                        1 -> SlideScanIllustration()
                        else -> SlideInsightsIllustration()
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Text(
                                text = stringResource(page.tag),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                        Text(
                            text = stringResource(page.title),
                            style = WasteNotAuthHeading,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = stringResource(page.body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            InsetGroupCard(glass = true, contentPadding = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(onboardingPages.size) { idx ->
                        val active = pagerState.currentPage == idx
                        val width by animateDpAsState(
                            targetValue = if (active) 28.dp else 8.dp,
                            animationSpec = tween(220),
                            label = "onboarding-dot-$idx",
                        )
                        val dotDesc = stringResource(R.string.onboarding_page_indicator, idx + 1, onboardingPages.size)
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                )
                                .clickable(role = Role.Tab) {
                                    scope.launch { pagerState.animateScrollToPage(idx) }
                                }
                                .semantics { contentDescription = dotDesc },
                        )
                    }
                }

                if (pagerState.currentPage < onboardingPages.lastIndex) {
                    ActionButton(
                        label = stringResource(R.string.onboarding_next),
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionButton(
                            label = stringResource(R.string.onboarding_start),
                            onClick = onStartRegister,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SecondaryActionButton(
                            label = stringResource(R.string.onboarding_login),
                            onClick = onStartLogin,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlideTrackingIllustration() {
    val tokens = LocalWasteNotSurfaceTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .summarySurface(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.handled_today),
                        style = MaterialTheme.typography.labelLarge,
                        color = SummarySecondaryText,
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = LimeStart,
                        contentColor = OnAccent,
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_preview_level_xp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.onboarding_preview_weight),
                    style = MaterialTheme.typography.displayLarge,
                    color = SummaryText,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_preview_activities),
                        style = MaterialTheme.typography.labelMedium,
                        color = SummaryText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = tokens.glassTintStrong,
            border = BorderStroke(1.dp, tokens.glassEdge),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bottle),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.category_plastic), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.onboarding_preview_item_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.onboarding_preview_item_weight), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun SlideScanIllustration() {
    val tokens = LocalWasteNotSurfaceTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF13261D),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color(0xFF3E634D)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF13261D), Color(0xFF244935)))),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawRoundRect(
                        color = LimeStart.copy(alpha = 0.72f),
                        topLeft = Offset(34.dp.toPx(), 24.dp.toPx()),
                        size = Size(size.width - 68.dp.toPx(), size.height - 48.dp.toPx()),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
                        ),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bottle),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = LimeStart,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = LimeStart,
                        contentColor = OnAccent,
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_preview_detected),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = tokens.glassTintStrong,
            border = BorderStroke(1.dp, tokens.glassEdge),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(LimeStart, LimeEnd))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_recycle),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = OnAccent,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.onboarding_preview_advice_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.onboarding_preview_advice_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SlideInsightsIllustration() {
    val tokens = LocalWasteNotSurfaceTokens.current
    val bars = remember { listOf(0.32f, 0.58f, 0.28f, 0.72f, 0.45f, 0.92f, 0.80f) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = tokens.glassTintStrong,
            border = BorderStroke(1.dp, tokens.glassEdge),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.onboarding_preview_trend), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.onboarding_preview_trend_weight), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(92.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    bars.forEachIndexed { idx, ratio ->
                        val active = idx >= bars.lastIndex - 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(ratio)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(
                                    if (active) Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, LimeEnd))
                                    else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primaryContainer)),
                                ),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricTile(
                label = stringResource(R.string.rate_title),
                value = stringResource(R.string.onboarding_preview_rate),
                icon = R.drawable.ic_recycle,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            MetricTile(
                label = stringResource(R.string.avoided_short_title),
                value = stringResource(R.string.onboarding_preview_avoided),
                icon = R.drawable.ic_leaf,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                limeHighlight = true,
            )
        }
    }
}
