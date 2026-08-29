package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Android View 页面与参考项目 Compose Bottom Tabs 之间的唯一桥接层。
 *
 * LiquidBottomTabs、阻尼拖动和互动高光均移植自 AndroidLiquidGlass-kmp；只将高度按
 * 现有 54dp 导航约束等比缩放，并把内容替换为本项目原有的四个图标。
 */
internal fun createCampusLiquidBottomTabsView(
    context: Context,
    initialIndex: Int,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    onTabSelected: (Int, View) -> Unit
): View = ComposeView(context).apply {
    setBackgroundColor(android.graphics.Color.TRANSPARENT)
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    setContent {
        var selectedIndex by rememberSaveable { mutableIntStateOf(initialIndex) }
        var hostOffsetInPage by remember { mutableStateOf(Offset.Zero) }
        var pageSize by remember { mutableStateOf(IntSize.Zero) }
        val backgroundImage = remember(pageBackgroundBitmap) {
            pageBackgroundBitmap?.asImageBitmap()
        }
        val backdrop = remember(hostOffsetInPage, pageSize, backgroundImage, pageBackgroundScrim) {
            SilkyPageGradientBackdrop(
                hostOffsetInPage = hostOffsetInPage,
                pageSize = pageSize,
                pageBackgroundImage = backgroundImage,
                pageBackgroundScrim = Color(pageBackgroundScrim)
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    val page = this@apply.parent as? View ?: return@onGloballyPositioned
                    val hostLocation = IntArray(2)
                    val pageLocation = IntArray(2)
                    this@apply.getLocationInWindow(hostLocation)
                    page.getLocationInWindow(pageLocation)
                    val nextOffset = Offset(
                        (hostLocation[0] - pageLocation[0]).toFloat(),
                        (hostLocation[1] - pageLocation[1]).toFloat()
                    )
                    val nextSize = IntSize(page.width, page.height)
                    if (hostOffsetInPage != nextOffset) hostOffsetInPage = nextOffset
                    if (pageSize != nextSize) pageSize = nextSize
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(Modifier.padding(bottom = 18.dp)) {
                LiquidBottomTabs(
                    selectedTabIndex = { selectedIndex },
                    onTabSelected = { index ->
                        selectedIndex = index
                        onTabSelected(index, this@apply)
                    },
                    backdrop = backdrop,
                    tabsCount = 4,
                    containerHeight = 54.dp,
                    indicatorHeight = 46.dp,
                    containerSurfaceAlpha = 0.34f,
                    modifier = Modifier
                        .width(216.dp)
                        .height(54.dp)
                ) {
                    repeat(4) { index ->
                        LiquidBottomTab(
                            onClick = {
                                if (selectedIndex == index) onTabSelected(index, this@apply)
                                else selectedIndex = index
                            }
                        ) {
                            LegacyNavigationIcon(index)
                        }
                    }
                }
            }
        }
    }
}

internal fun createLoginLiquidModeToggleView(
    context: Context,
    initialIndex: Int,
    onTabSelected: (Int) -> Unit,
    onPositionDragged: (Float) -> Unit,
    onDragFinished: (Float, Float) -> Unit
): LoginLiquidModeToggleView = LoginLiquidModeToggleView(
    context = context,
    initialIndex = initialIndex,
    onTabSelected = onTabSelected,
    onPositionDragged = onPositionDragged,
    onDragFinished = onDragFinished
)

internal class LoginLiquidModeToggleView(
    context: Context,
    initialIndex: Int,
    private val onTabSelected: (Int) -> Unit,
    private val onPositionDragged: (Float) -> Unit,
    private val onDragFinished: (Float, Float) -> Unit
) : FrameLayout(context) {
    private var positionState by mutableFloatStateOf(initialIndex.toFloat())
    private var selectedIndexState by mutableIntStateOf(initialIndex)

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(
            ComposeView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    val backdrop = rememberCanvasBackdrop {
                        // Only the capsule draws a surface. A transparent source avoids
                        // exposing the rectangular Compose host around its four corners.
                        drawRect(Color.Transparent)
                    }
                    LiquidBottomTabs(
                        selectedTabIndex = { selectedIndexState },
                        onTabSelected = { index ->
                            selectedIndexState = index
                            onTabSelected(index)
                        },
                        backdrop = backdrop,
                        tabsCount = 2,
                        containerHeight = 60.dp,
                        indicatorHeight = 52.dp,
                        externalPosition = { positionState },
                        onPositionChanged = { position ->
                            updatePosition(position)
                            onPositionDragged(position)
                        },
                        onDragFinished = onDragFinished,
                        referenceStyle = true,
                        refractContent = false,
                        pressedScale = 1.14f,
                        contentPressedScale = 1.08f,
                        indicatorLensHorizontal = 2.dp,
                        indicatorLensVertical = 4.dp,
                        indicatorChromaticAberration = false,
                        containerSurfaceAlpha = 0.40f,
                        restingIndicatorAlpha = 0.05f,
                        indicatorShadowEnabled = false,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LiquidBottomTab(onClick = { selectedIndexState = 0 }) {
                            LoginLiquidTabLabel("个人课表", 0, positionState)
                        }
                        LiquidBottomTab(onClick = { selectedIndexState = 1 }) {
                            LoginLiquidTabLabel("全校课表", 1, positionState)
                        }
                    }
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun setSelectionPosition(position: Float) {
        updatePosition(position)
    }

    private fun updatePosition(position: Float) {
        positionState = position.coerceIn(0f, 1f)
    }

    fun setSettledIndex(index: Int) {
        selectedIndexState = index.coerceIn(0, 1)
        positionState = selectedIndexState.toFloat()
    }
}

@Composable
private fun LoginLiquidTabLabel(label: String, index: Int, selectionPosition: Float) {
    val selectedAmount = (1f - abs(selectionPosition - index.toFloat())).coerceIn(0f, 1f)
    BasicText(
        text = label,
        style = TextStyle(
            color = lerpColor(Color.Black, Color(0xFF0088FF), selectedAmount),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    )
}

internal class SilkyPageGradientBackdrop(
    private val hostOffsetInPage: Offset,
    private val pageSize: IntSize,
    private val pageBackgroundImage: ImageBitmap? = null,
    private val pageBackgroundScrim: Color = Color.Transparent
) : Backdrop {
    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val targetOffsetInHost =
            if (coordinates?.isAttached == true) coordinates.positionInRoot() else Offset.Zero
        val targetOffsetInPage = hostOffsetInPage + targetOffsetInHost
        val pageWidth = pageSize.width.takeIf { it > 0 }?.toFloat() ?: size.width
        val pageHeight = pageSize.height.takeIf { it > 0 }?.toFloat() ?: size.height
        val image = pageBackgroundImage
        if (image != null) {
            val scale = maxOf(
                pageWidth / image.width.coerceAtLeast(1),
                pageHeight / image.height.coerceAtLeast(1)
            )
            val scaledWidth = image.width * scale
            val scaledHeight = image.height * scale
            val pageImageLeft = (pageWidth - scaledWidth) / 2f
            val pageImageTop = (pageHeight - scaledHeight) / 2f
            drawImage(
                image = image,
                dstOffset = IntOffset(
                    (pageImageLeft - targetOffsetInPage.x).fastRoundToInt(),
                    (pageImageTop - targetOffsetInPage.y).fastRoundToInt()
                ),
                dstSize = IntSize(
                    scaledWidth.fastRoundToInt().coerceAtLeast(1),
                    scaledHeight.fastRoundToInt().coerceAtLeast(1)
                )
            )
            if (pageBackgroundScrim.alpha > 0f) drawRect(pageBackgroundScrim)
        } else {
            drawRect(
                brush = Brush.linearGradient(
                    colors = silkyGradientSamples,
                    start = Offset(-targetOffsetInPage.x, -targetOffsetInPage.y),
                    end = Offset(
                        pageWidth - targetOffsetInPage.x,
                        pageHeight - targetOffsetInPage.y
                    )
                )
            )
        }
    }
}

private val silkyGradientAnchors = listOf(
    Color(0xFFF3F2F9),
    Color(0xFFF0F1F9),
    Color(0xFFEBEFF8),
    Color(0xFFE3EBF7),
    Color(0xFFD9E5F4)
)

private val silkyGradientSamples = List(65) { sampleIndex ->
    val position = sampleIndex / 64f
    val lastSegment = silkyGradientAnchors.size - 2
    val scaled = position * (silkyGradientAnchors.size - 1)
    val segment = scaled.toInt().coerceIn(0, lastSegment)
    val t = (scaled - segment).coerceIn(0f, 1f)
    val p0 = silkyGradientAnchors[(segment - 1).coerceAtLeast(0)]
    val p1 = silkyGradientAnchors[segment]
    val p2 = silkyGradientAnchors[segment + 1]
    val p3 = silkyGradientAnchors[(segment + 2).coerceAtMost(silkyGradientAnchors.lastIndex)]
    fun channel(a: Float, b: Float, c: Float, d: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return (0.5f * (
            2f * b + (-a + c) * t +
                (2f * a - 5f * b + 4f * c - d) * t2 +
                (-a + 3f * b - 3f * c + d) * t3
            )).coerceIn(0f, 1f)
    }
    Color(
        red = channel(p0.red, p1.red, p2.red, p3.red),
        green = channel(p0.green, p1.green, p2.green, p3.green),
        blue = channel(p0.blue, p1.blue, p2.blue, p3.blue),
        alpha = 1f
    )
}

private val LocalLiquidBottomTabScale = staticCompositionLocalOf { { 1f } }

@Composable
private fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val currentScale = scale()
                scaleX = currentScale
                scaleY = currentScale
            },
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
private fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    containerHeight: Dp,
    indicatorHeight: Dp,
    externalPosition: (() -> Float)? = null,
    onPositionChanged: ((Float) -> Unit)? = null,
    onDragFinished: ((Float, Float) -> Unit)? = null,
    referenceStyle: Boolean = false,
    refractContent: Boolean = true,
    pressedScale: Float = 78f / 56f,
    contentPressedScale: Float = 1.2f,
    indicatorLensHorizontal: Dp = 10.dp,
    indicatorLensVertical: Dp = 14.dp,
    indicatorChromaticAberration: Boolean = true,
    containerSurfaceAlpha: Float? = null,
    restingIndicatorAlpha: Float = 0.10f,
    indicatorShadowEnabled: Boolean = referenceStyle,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    // 主页面始终使用浅色背景，不能跟随系统深色模式切成黑色玻璃。
    val accentColor = Color(0xFF0088FF)
    val containerColor = Color(0xFFFAFAFA).copy(
        alpha = containerSurfaceAlpha ?: if (referenceStyle) 0.40f else 0.26f
    )
    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val horizontalInset = 4.dp
        val horizontalInsetPx = with(density) { horizontalInset.toPx() }
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - horizontalInsetPx * 2f) / tabsCount
        }
        val tabWidthDp = with(density) { tabWidth.toDp() }
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
        val isLtr = androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex())
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                // 原组件所在父容器有额外留白；本项目固定为 216x54dp，过度放大会被裁成直边。
                pressedScale = pressedScale,
                onDragStarted = {},
                onDragStopped = {
                    onDragFinished?.invoke(targetValue, velocity)
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    val nextValue =
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    updateValue(nextValue)
                    onPositionChanged?.invoke(nextValue)
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }.collectLatest { index -> currentIndex = index }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
                dampedDragAnimation.animateToValue(index.toFloat())
                onTabSelected(index)
            }
        }
        val indicatorValueState = remember(externalPosition, dampedDragAnimation, tabsCount) {
            derivedStateOf {
                (
                    if (dampedDragAnimation.isDragging) dampedDragAnimation.value
                    else externalPosition?.invoke() ?: dampedDragAnimation.value
                    ).fastCoerceIn(0f, (tabsCount - 1).toFloat())
            }
        }
        val indicatorValue = indicatorValueState.value
        val currentIndicatorValue = rememberUpdatedState(indicatorValue)
        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) horizontalInsetPx +
                            (currentIndicatorValue.value + 0.5f) * tabWidth + panelOffset
                        else size.width - horizontalInsetPx -
                            (currentIndicatorValue.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                // Android's runtime blur can bleed into the rectangular offscreen layer
                // around the capsule. Clip only the resting track so those four corners
                // stay transparent; the independent liquid indicator can still grow
                // above and below the 60dp track while pressed or dragged.
                .clip(Capsule())
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    shadow = null,
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(containerHeight)
                .fillMaxWidth()
                .padding(horizontal = horizontalInset, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        if (refractContent) {
            CompositionLocalProvider(
                LocalLiquidBottomTabScale provides {
                    lerp(1f, contentPressedScale, dampedDragAnimation.pressProgress)
                }
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .clip(Capsule())
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur(8f.dp.toPx())
                                lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                            },
                            highlight = {
                                Highlight.Default.copy(
                                    alpha = dampedDragAnimation.pressProgress *
                                        if (referenceStyle) 1f else 0.35f
                                )
                            },
                            shadow = null,
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .then(interactiveHighlight.modifier)
                        .height(indicatorHeight)
                        .fillMaxWidth()
                        .padding(horizontal = horizontalInset)
                        .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        }

        val indicatorBackdrop = if (refractContent) {
            rememberCombinedBackdrop(backdrop, tabsBackdrop)
        } else {
            backdrop
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        if (isLtr) horizontalInsetPx +
                            indicatorValue * tabWidth + panelOffset
                        else constraints.maxWidth.toFloat() - horizontalInsetPx -
                            (indicatorValue + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = indicatorBackdrop,
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            indicatorLensHorizontal.toPx() * progress,
                            indicatorLensVertical.toPx() * progress,
                            chromaticAberration = indicatorChromaticAberration
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(
                            alpha = dampedDragAnimation.pressProgress *
                                if (referenceStyle) 1f else 0.35f
                        )
                    },
                    shadow = if (indicatorShadowEnabled) {
                        {
                            Shadow(alpha = dampedDragAnimation.pressProgress)
                        }
                    } else {
                        null
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress * if (referenceStyle) 1f else 0.45f
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            Color.Black.copy(restingIndicatorAlpha),
                            alpha = 1f - progress
                        )
                        if (referenceStyle) {
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        }
                    }
                )
                .height(indicatorHeight)
                .width(tabWidthDp)
        )
    }
}

@Composable
private fun LegacyNavigationIcon(index: Int) {
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val s = 8.dp.toPx()
        fun drawGlyph(color: Color, stroke: Stroke, verticalOffset: Float) {
            val cx = size.width / 2f
            val cy = size.height / 2f + verticalOffset
            when (index) {
                0 -> {
                    val left = cx - s
                    val top = cy - s * .72f
                    val right = cx + s
                    val bottom = cy + s * .78f
                    drawRoundRect(
                        color,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx()),
                        style = stroke
                    )
                    drawLine(color, Offset(left, cy - s * .28f), Offset(right, cy - s * .28f), stroke.width, stroke.cap)
                    drawLine(color, Offset(cx - s * .48f, cy - s), Offset(cx - s * .48f, cy - s * .5f), stroke.width, stroke.cap)
                    drawLine(color, Offset(cx + s * .48f, cy - s), Offset(cx + s * .48f, cy - s * .5f), stroke.width, stroke.cap)
                }
                1 -> {
                    val left = cx - s * .72f
                    val top = cy - s
                    val right = cx + s * .72f
                    val bottom = cy + s
                    drawRoundRect(
                        color,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                        style = stroke
                    )
                    drawLine(color, Offset(cx - s * .4f, cy - s * .48f), Offset(cx + s * .38f, cy - s * .48f), stroke.width, stroke.cap)
                    drawLine(color, Offset(cx - s * .4f, cy - s * .08f), Offset(cx + s * .2f, cy - s * .08f), stroke.width, stroke.cap)
                    drawCircle(color, s * .28f, Offset(cx + s * .34f, cy + s * .48f), style = stroke)
                    drawLine(color, Offset(cx + s * .34f, cy + s * .48f), Offset(cx + s * .34f, cy + s * .31f), stroke.width, stroke.cap)
                    drawLine(color, Offset(cx + s * .34f, cy + s * .48f), Offset(cx + s * .47f, cy + s * .56f), stroke.width, stroke.cap)
                }
                2 -> {
                    drawLine(color, Offset(cx - s, cy + s * .9f), Offset(cx + s, cy + s * .9f), stroke.width, stroke.cap)
                    drawLine(color, Offset(cx - s * .65f, cy + s * .9f), Offset(cx - s * .65f, cy + s * .1f), stroke.width, stroke.cap)
                    drawLine(color, Offset(cx, cy + s * .9f), Offset(cx, cy - s * .45f), stroke.width, stroke.cap)
                    drawLine(color, Offset(cx + s * .65f, cy + s * .9f), Offset(cx + s * .65f, cy - s), stroke.width, stroke.cap)
                }
                3 -> {
                    drawCircle(color, s * .58f, Offset(cx - s * .18f, cy - s * .18f), style = stroke)
                    drawLine(
                        color,
                        Offset(cx + s * .23f, cy + s * .23f),
                        Offset(cx + s * .86f, cy + s * .86f),
                        stroke.width,
                        stroke.cap
                    )
                }
            }
        }

        drawGlyph(
            color = Color(0xFF111318),
            stroke = Stroke(1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            verticalOffset = 0f
        )
    }
}

private class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedRange<Float>,
    visibilityThreshold: Float,
    private val initialScale: Float,
    private val pressedScale: Float,
    private val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    private val onDragStopped: DampedDragAnimation.() -> Unit,
    private val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit
) {
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)
    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)
    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()
    var isDragging by mutableStateOf(false)
        private set

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                isDragging = true
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                isDragging = false
                release()
            },
            onDragCancel = {
                onDragStopped()
                isDragging = false
                release()
            }
        ) { _, dragAmount -> onDrag(size, dragAmount) }
    }

    private fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    private fun release() {
        animationScope.launch {
            withFrameNanos { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(target, valueAnimationSpec) { updateVelocity() } }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val target = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(target, valueAnimationSpec) }
                if (velocity != 0f) launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val targetVelocity = velocityTracker.calculateVelocity().x /
            (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}

internal class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {
    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition
    private val shader = if (isRuntimeShaderSupported()) {
        RuntimeShader(
            """
            uniform float2 size;
            layout(color) uniform half4 color;
            uniform float radius;
            uniform float2 position;

            half4 main(float2 coord) {
                float dist = distance(coord, position);
                float intensity = smoothstep(radius, radius * 0.5, dist);
                return color * intensity;
            }
            """.trimIndent()
        )
    } else null

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            if (shader != null) {
                drawRect(Color.White.copy(0.08f * progress), blendMode = BlendMode.Plus)
                shader.apply {
                    val currentPosition = position(size, positionAnimation.value)
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", Color.White.copy(0.15f * progress))
                    setFloatUniform("radius", size.minDimension * 1.5f)
                    setFloatUniform(
                        "position",
                        currentPosition.x.fastCoerceIn(0f, size.width),
                        currentPosition.y.fastCoerceIn(0f, size.height)
                    )
                }
                drawRect(ShaderBrush(shader.asComposeShader()), blendMode = BlendMode.Plus)
            } else {
                drawRect(Color.White.copy(0.25f * progress), blendMode = BlendMode.Plus)
            }
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            }
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }
}

private suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        val drag = initialDown
        onDragStart(down)
        onDrag(drag, Offset.Zero)
        val upEvent = drag(drag.id) { onDrag(it, it.positionChange()) }
        if (upEvent == null) onDragCancel() else onDragEnd(upEvent)
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    if (currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) return dragEvent
            pointer = otherDown.id
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}
