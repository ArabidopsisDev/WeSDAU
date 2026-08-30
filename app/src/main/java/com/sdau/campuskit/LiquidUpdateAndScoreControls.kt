package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.math.roundToInt

/** View 页面和参考项目 Compose Liquid Dialog 之间的桥接层。 */
internal class LiquidUpdateDialogView(
    context: Context,
    private var pageSnapshot: Bitmap?,
    versionName: String,
    changelog: String,
    forced: Boolean,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) : FrameLayout(context) {
    private var downloadInProgress by mutableStateOf(false)

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        addView(
            ComposeView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    LiquidUpdateDialog(
                        pageSnapshot = pageSnapshot,
                        versionName = versionName,
                        changelog = changelog,
                        forced = forced,
                        downloading = downloadInProgress,
                        onDismiss = onDismiss,
                        onUpdate = onUpdate
                    )
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun setDownloading(value: Boolean) {
        downloadInProgress = value
    }

    fun releaseSnapshot() {
        val bitmap = pageSnapshot
        pageSnapshot = null
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }
}

internal data class LiquidPickerOption(
    val title: String,
    val subtitle: String = "",
    val iconRes: Int = 0,
    val selected: Boolean = false,
    val onClick: () -> Unit
)

internal data class LiquidMenuAction(
    val title: String,
    val iconRes: Int,
    val dividerAfter: Boolean = false,
    val onClick: () -> Unit
)

/** Compact top-right action menu backed by the same live page sampling as the dialogs. */
internal class LiquidActionMenuView(
    context: Context,
    private var pageSnapshot: Bitmap?,
    menuX: Int,
    menuY: Int,
    actions: List<LiquidMenuAction>,
    onDismiss: () -> Unit
) : FrameLayout(context) {
    private var updateStatus by mutableStateOf("")
    private var checkingUpdate by mutableStateOf(false)

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        addView(
            ComposeView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    LiquidActionMenu(
                        pageSnapshot = pageSnapshot,
                        menuX = menuX,
                        menuY = menuY,
                        actions = actions,
                        updateStatus = updateStatus,
                        checkingUpdate = checkingUpdate,
                        onDismiss = onDismiss
                    )
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun setUpdateStatus(message: String, checking: Boolean) {
        updateStatus = message
        checkingUpdate = checking
    }

    fun releaseSnapshot() {
        val bitmap = pageSnapshot
        pageSnapshot = null
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }
}

@Composable
private fun LiquidActionMenu(
    pageSnapshot: Bitmap?,
    menuX: Int,
    menuY: Int,
    actions: List<LiquidMenuAction>,
    updateStatus: String,
    checkingUpdate: Boolean,
    onDismiss: () -> Unit
) {
    val snapshotImage = remember(pageSnapshot) { pageSnapshot?.asImageBitmap() }
    val backdrop = rememberLayerBackdrop()
    val panelShape = RoundedCornerShape(22.dp)
    val contentColor = Color(0xFF171923)
    val accentColor = Color(0xFF0088FF)
    var slidingIndex by remember { mutableStateOf<Int?>(null) }
    val actionBounds = remember(actions) { mutableMapOf<Int, Pair<Float, Float>>() }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            if (snapshotImage != null) {
                Image(
                    bitmap = snapshotImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFFE9EFF8)))
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
        )
        Column(
            Modifier
                .offset { IntOffset(menuX, menuY) }
                .width(204.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(22.dp) },
                    effects = {
                        vibrancy()
                        colorControls(brightness = 0.06f, saturation = 1.25f)
                        blur(12.dp.toPx())
                        lens(12.dp.toPx(), 24.dp.toPx(), depthEffect = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.68f) },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.30f)) }
                )
                .clip(panelShape)
                .pointerInput(actions, checkingUpdate) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun actionIndexAt(y: Float): Int? = actionBounds.entries
                            .firstOrNull { (_, bounds) -> y >= bounds.first && y <= bounds.second }
                            ?.key
                            ?.takeUnless { it == 0 && checkingUpdate }

                        slidingIndex = actionIndexAt(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            slidingIndex = actionIndexAt(change.position.y)
                            if (!change.pressed) {
                                val selected = slidingIndex
                                slidingIndex = null
                                if (selected != null) actions[selected].onClick()
                                break
                            }
                            change.consume()
                        }
                    }
                }
                .padding(6.dp)
        ) {
            actions.forEachIndexed { index, action ->
                val isUpdateAction = index == 0
                val rowHeight = if (isUpdateAction && updateStatus.isNotBlank()) 54.dp else 44.dp
                val highlightProgress by animateFloatAsState(
                    targetValue = if (slidingIndex == index) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
                    label = "menuItemHighlight"
                )
                val itemShape = RoundedCornerShape(14.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .onGloballyPositioned { coordinates ->
                            val top = coordinates.positionInParent().y
                            actionBounds[index] = top to (top + coordinates.size.height)
                        }
                        .graphicsLayer {
                            val selectedScale = 1f + highlightProgress * 0.018f
                            scaleX = selectedScale
                            scaleY = selectedScale
                        }
                        .clip(itemShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.48f * highlightProgress),
                                    Color.White.copy(alpha = 0.17f * highlightProgress),
                                    Color(0xFFBDE5FF).copy(alpha = 0.23f * highlightProgress)
                                ),
                                start = Offset.Zero,
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            ),
                            shape = itemShape
                        )
                        .background(
                            color = if (isUpdateAction && checkingUpdate) {
                                Color.White.copy(alpha = 0.18f)
                            } else {
                                Color.Transparent
                            },
                            shape = itemShape
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.88f * highlightProgress),
                                    Color(0xFF98D7FF).copy(alpha = 0.42f * highlightProgress),
                                    Color.White.copy(alpha = 0.62f * highlightProgress)
                                )
                            ),
                            shape = itemShape
                        )
                        .semantics {
                            role = Role.Button
                            contentDescription = action.title
                        }
                        .padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(action.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        colorFilter = ColorFilter.tint(accentColor)
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    ) {
                        BasicText(
                            action.title,
                            style = TextStyle(contentColor, 14.sp, FontWeight.Medium)
                        )
                        if (isUpdateAction && updateStatus.isNotBlank()) {
                            BasicText(
                                updateStatus,
                                modifier = Modifier.padding(top = 2.dp),
                                style = TextStyle(contentColor.copy(alpha = 0.64f), 10.sp)
                            )
                        }
                    }
                }
                if (action.dividerAfter) {
                    Box(
                        Modifier
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.42f))
                    )
                }
            }
        }
    }
}

/** Generic picker that uses the exact same captured-page glass shell as the update dialog. */
internal class LiquidPickerDialogView(
    context: Context,
    private var pageSnapshot: Bitmap?,
    title: String,
    options: List<LiquidPickerOption>,
    onDismiss: () -> Unit
) : FrameLayout(context) {
    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        addView(
            ComposeView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    LiquidPickerDialog(
                        pageSnapshot = pageSnapshot,
                        title = title,
                        options = options,
                        onDismiss = onDismiss
                    )
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun releaseSnapshot() {
        val bitmap = pageSnapshot
        pageSnapshot = null
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }
}

@Composable
private fun LiquidPickerDialog(
    pageSnapshot: Bitmap?,
    title: String,
    options: List<LiquidPickerOption>,
    onDismiss: () -> Unit
) {
    val contentColor = Color(0xFF171923)
    val accentColor = Color(0xFF0088FF)
    val containerColor = Color(0xFFFAFAFA).copy(alpha = 0.42f)
    val dimColor = Color(0xFF29293A).copy(alpha = 0.23f)
    val snapshotImage = remember(pageSnapshot) { pageSnapshot?.asImageBitmap() }
    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            if (snapshotImage != null) {
                Image(
                    bitmap = snapshotImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFFE9EFF8)))
            }
            Box(Modifier.fillMaxSize().background(dimColor))
        }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onDismiss
                )
        )
        Column(
            Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(48.dp) },
                    effects = {
                        colorControls(brightness = 0.08f, saturation = 1.35f)
                        blur(12.dp.toPx())
                        lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = true)
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = {}
                )
        ) {
            BasicText(
                title,
                modifier = Modifier.padding(28.dp, 24.dp, 28.dp, 16.dp),
                style = TextStyle(contentColor, 24.sp, FontWeight.Medium)
            )
            Column(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                    .heightIn(max = 330.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    val optionForeground = contentColor
                    CampusLiquidButton(
                        onClick = option.onClick,
                        backdrop = backdrop,
                        style = LiquidButtonStyle.FROSTED,
                        enabled = true,
                        allowDragDeformation = false,
                        deformationHorizontalPadding = 4.dp,
                        deformationVerticalPadding = 4.dp,
                        modifier = Modifier.fillMaxWidth(),
                        height = 68.dp
                    ) {
                        if (option.iconRes != 0) {
                            Image(
                                painter = painterResource(option.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                colorFilter = ColorFilter.tint(accentColor)
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(width = 5.dp, height = 28.dp)
                                    .clip(Capsule())
                                    .background(accentColor)
                            )
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 13.dp)
                        ) {
                            BasicText(
                                option.title,
                                style = TextStyle(optionForeground, 16.sp, FontWeight.SemiBold)
                            )
                            if (option.subtitle.isNotBlank()) {
                                BasicText(
                                    option.subtitle,
                                    modifier = Modifier.padding(top = 3.dp),
                                    style = TextStyle(optionForeground.copy(alpha = 0.68f), 12.sp)
                                )
                            }
                        }
                        if (option.selected) {
                            BasicText("✓", style = TextStyle(accentColor, 18.sp, FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidUpdateDialog(
    pageSnapshot: Bitmap?,
    versionName: String,
    changelog: String,
    forced: Boolean,
    downloading: Boolean,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    val contentColor = Color(0xFF171923)
    val secondaryColor = contentColor.copy(alpha = 0.68f)
    val accentColor = Color(0xFF0088FF)
    val containerColor = Color(0xFFFAFAFA).copy(alpha = 0.42f)
    val dimColor = Color(0xFF29293A).copy(alpha = 0.23f)
    val snapshotImage = remember(pageSnapshot) { pageSnapshot?.asImageBitmap() }
    val backdrop = rememberLayerBackdrop()

    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Recreate the reference BackdropDemoScaffold: the actual captured page is
        // exported as a LayerBackdrop, then every dialog surface samples that layer.
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            if (snapshotImage != null) {
                Image(
                    bitmap = snapshotImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFFE9EFF8)))
            }
            Box(Modifier.fillMaxSize().background(dimColor))
        }
        Box(
            Modifier
                .fillMaxSize()
            .clickable(
                enabled = !forced,
                interactionSource = null,
                indication = null,
                onClick = onDismiss
            )
        )
        Column(
            Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(48.dp) },
                    effects = {
                        colorControls(brightness = 0.08f, saturation = 1.35f)
                        blur(12.dp.toPx())
                        lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = true)
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = {}
                )
        ) {
            BasicText(
                text = "发现新版本",
                modifier = Modifier.padding(28.dp, 24.dp, 28.dp, 8.dp),
                style = TextStyle(contentColor, 24.sp, FontWeight.Medium)
            )
            BasicText(
                text = versionName,
                modifier = Modifier.padding(horizontal = 28.dp),
                style = TextStyle(accentColor, 14.sp, FontWeight.SemiBold)
            )
            BasicText(
                text = "更新内容",
                modifier = Modifier.padding(24.dp, 18.dp, 24.dp, 6.dp),
                style = TextStyle(secondaryColor, 13.sp, FontWeight.SemiBold)
            )
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .heightIn(min = 64.dp, max = 220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                BasicText(
                    text = changelog,
                    style = TextStyle(contentColor.copy(alpha = 0.78f), 15.sp, lineHeight = 23.sp)
                )
            }
            Row(
                Modifier
                    .padding(24.dp, 18.dp, 24.dp, 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!forced) {
                    DialogAction(
                        label = "稍后",
                        style = LiquidButtonStyle.TRANSPARENT,
                        backdrop = backdrop,
                        foreground = contentColor,
                        enabled = !downloading,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }
                DialogAction(
                    label = if (downloading) "正在下载…" else "立即更新",
                    style = LiquidButtonStyle.TINTED,
                    backdrop = backdrop,
                    foreground = Color.White,
                    enabled = !downloading,
                    onClick = onUpdate,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DialogAction(
    label: String,
    style: LiquidButtonStyle,
    backdrop: com.kyant.backdrop.Backdrop,
    foreground: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (style == LiquidButtonStyle.TRANSPARENT) {
        // Match the reference Dialog's Cancel action exactly: this is a quiet,
        // translucent capsule rather than a second refractive/tinted button.
        // Avoiding drawBackdrop here also prevents the cancel action from looking gray.
        Row(
            modifier
                .height(48.dp)
                .clip(Capsule())
                .background(Color(0xFFFAFAFA).copy(alpha = 0.20f))
                .clickable(
                    enabled = enabled,
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                label,
                style = TextStyle(foreground.copy(alpha = if (enabled) 1f else 0.72f), 16.sp)
            )
        }
        return
    }

    CampusLiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        style = style,
        enabled = enabled,
        allowDragDeformation = false,
        deformationHorizontalPadding = 4.dp,
        deformationVerticalPadding = 4.dp,
        modifier = modifier,
        height = 48.dp
    ) {
        BasicText(
            label,
            style = TextStyle(foreground.copy(alpha = if (enabled) 1f else 0.72f), 16.sp)
        )
    }
}

private enum class LiquidButtonStyle { TRANSPARENT, SURFACE, FROSTED, TINTED }

/** 与参考项目 LiquidButton 一致的 Surface/Tinted 绘制核心。 */
@Composable
private fun CampusLiquidButton(
    onClick: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop,
    style: LiquidButtonStyle,
    enabled: Boolean,
    allowDragDeformation: Boolean = true,
    deformationHorizontalPadding: androidx.compose.ui.unit.Dp = 14.dp,
    deformationVerticalPadding: androidx.compose.ui.unit.Dp = 4.dp,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 48.dp,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val accentColor = Color(0xFF0088FF)
    Box(
        modifier = modifier
            .height(height)
            // Runtime blur is rendered through a rectangular offscreen layer. Keep
            // deformation room inside the host, but never expose that layer's corners.
            .clip(Capsule()),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .fillMaxSize()
                // Keep the Android host size unchanged while reserving deformation room.
                .padding(
                    horizontal = deformationHorizontalPadding,
                    vertical = deformationVerticalPadding
                )
                .graphicsLayer { alpha = if (enabled) 1f else 0.62f }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        if (style == LiquidButtonStyle.FROSTED) {
                            colorControls(brightness = 0.14f, saturation = 0.62f)
                            blur(18.dp.toPx())
                            lens(4.dp.toPx(), 8.dp.toPx())
                        } else {
                            vibrancy()
                            blur(2.dp.toPx())
                            lens(12.dp.toPx(), 24.dp.toPx())
                        }
                    },
                    layerBlock = if (enabled) {
                        {
                            val width = size.width
                            val heightPx = size.height
                            val progress = interactiveHighlight.pressProgress
                            val scale = lerp(1f, 1f + 4.dp.toPx() / heightPx, progress)
                            scaleX = scale
                            scaleY = scale
                            if (allowDragDeformation) {
                                val maxOffset = size.minDimension
                                val offset = interactiveHighlight.offset
                                translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                                translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
                                val maxDragScale = 4.dp.toPx() / heightPx
                                val offsetAngle = atan2(offset.y, offset.x)
                                scaleX += maxDragScale *
                                    abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                    (width / heightPx).fastCoerceAtMost(1f)
                                scaleY += maxDragScale *
                                    abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                    (heightPx / width).fastCoerceAtMost(1f)
                            } else {
                                translationX = 0f
                                translationY = 0f
                            }
                        }
                    } else {
                        null
                    },
                    shadow = null,
                    onDrawSurface = {
                        when (style) {
                            // Reference Dialog cancel button: a neutral white glass wash,
                            // without the previous gray-looking surface.
                            LiquidButtonStyle.TRANSPARENT ->
                                drawRect(Color.White.copy(alpha = 0.12f))
                            LiquidButtonStyle.SURFACE -> drawRect(Color.White.copy(alpha = 0.30f))
                            LiquidButtonStyle.FROSTED ->
                                drawRect(Color(0xFFF4F6FA).copy(alpha = 0.68f))
                            LiquidButtonStyle.TINTED -> {
                                drawRect(accentColor, blendMode = BlendMode.Hue)
                                drawRect(accentColor.copy(alpha = 0.75f))
                            }
                        }
                    }
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
                .then(
                    if (enabled) {
                        Modifier
                            .then(interactiveHighlight.modifier)
                            .then(interactiveHighlight.gestureModifier)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** Android View 表单使用的蓝色 Tinted Liquid Button 桥接层。 */
internal class LiquidTintedActionButtonView(
    context: Context,
    initialText: String,
    private val buttonHeightDp: Int,
    onClick: () -> Unit
) : FrameLayout(context) {
    private var labelState by mutableStateOf(initialText)
    private var buttonEnabledState by mutableStateOf(true)

    var text: CharSequence
        get() = labelState
        set(value) {
            labelState = value.toString()
        }

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(
            composeHostView(context) {
                // A transparent CanvasBackdrop still allocates a filtered rectangle
                // on Android and can leave a faint box around the capsule. Tinted
                // buttons only need their own blue surface and interaction highlight,
                // so an empty source avoids that offscreen residue completely.
                val backdrop = emptyBackdrop()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CampusLiquidButton(
                        onClick = onClick,
                        backdrop = backdrop,
                        style = LiquidButtonStyle.TINTED,
                        enabled = buttonEnabledState,
                        allowDragDeformation = false,
                        modifier = Modifier.fillMaxWidth(),
                        height = buttonHeightDp.dp
                    ) {
                        BasicText(
                            labelState,
                            style = TextStyle(Color.White, 16.sp, FontWeight.SemiBold)
                        )
                    }
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun setButtonEnabled(enabled: Boolean) {
        buttonEnabledState = enabled
    }
}

private fun composeHostView(
    context: Context,
    content: @Composable () -> Unit
): ComposeView = ComposeView(context).apply {
    setBackgroundColor(android.graphics.Color.TRANSPARENT)
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    setContent(content)
}

/**
 * Full score result page using the same structure as the reference ScrollContainer:
 * one fixed exported LayerBackdrop and one Compose-owned scrolling column. Keeping
 * both in the same composition makes the lens sample new background coordinates on
 * every scroll frame instead of translating a pre-rendered Android child layer.
 */
internal fun createScoreLiquidScrollPageView(
    context: Context,
    result: RemoteScoreResult,
    scoreColors: List<Int>,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    onTermClick: () -> Unit,
    onScoreClick: (RemoteScore) -> Unit,
    onExport: () -> Unit
): View = composeHostView(context) {
    ScoreLiquidScrollPage(
        result = result,
        scoreColors = scoreColors,
        pageBackgroundBitmap = pageBackgroundBitmap,
        pageBackgroundScrim = pageBackgroundScrim,
        onTermClick = onTermClick,
        onScoreClick = onScoreClick,
        onExport = onExport
    )
}

/**
 * Exam result page backed by the same live LayerBackdrop used by score cards.
 * Keeping the background source and the scrolling cards in one composition lets
 * every card sample its current screen coordinates on each scroll frame.
 */
internal fun createExamLiquidScrollPageView(
    context: Context,
    term: String,
    records: List<RemoteExam>,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int
): View = composeHostView(context) {
    ExamLiquidScrollPage(
        term = term,
        records = records,
        pageBackgroundBitmap = pageBackgroundBitmap,
        pageBackgroundScrim = pageBackgroundScrim
    )
}

internal fun createEmptyRoomLiquidGroupCardView(
    context: Context,
    groupKey: String,
    title: String,
    accentColor: Int,
    rooms: List<String>,
    initiallyExpanded: Boolean,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    onExpandedChanged: (Boolean) -> Unit
): View = composeHostView(context) {
    EmptyRoomLiquidGroupCard(
        groupKey = groupKey,
        title = title,
        accentColor = accentColor,
        rooms = rooms,
        initiallyExpanded = initiallyExpanded,
        pageBackgroundBitmap = pageBackgroundBitmap,
        pageBackgroundScrim = pageBackgroundScrim,
        onExpandedChanged = onExpandedChanged
    )
}

@Composable
private fun EmptyRoomLiquidGroupCard(
    groupKey: String,
    title: String,
    accentColor: Int,
    rooms: List<String>,
    initiallyExpanded: Boolean,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    onExpandedChanged: (Boolean) -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    val pageBackgroundImage = remember(pageBackgroundBitmap) {
        pageBackgroundBitmap?.asImageBitmap()
    }
    val pageGradient = Brush.linearGradient(
        listOf(
            Color(0xFFF3F2F9),
            Color(0xFFF0F1F9),
            Color(0xFFEBEFF8),
            Color(0xFFE3EBF7),
            Color(0xFFD9E5F4)
        )
    )
    val textPrimary = Color(0xFF1C2230)
    var expanded by remember(groupKey) { mutableStateOf(initiallyExpanded) }
    val roomRows = remember(rooms) { rooms.chunked(2) }

    val cardShape = RoundedRectangle(20.dp)
    Box(
        Modifier
            .fillMaxWidth()
            // The backdrop source lives inside this embedded ComposeView. Clip the
            // whole host to the card shape so its rectangular layer can never leak.
            .clip(cardShape)
    ) {
        PageAlignedBackdropSource(
            backdrop = backdrop,
            pageBackgroundImage = pageBackgroundImage,
            pageBackgroundScrim = pageBackgroundScrim,
            pageGradient = pageGradient
        )
        Column(
            Modifier
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { cardShape },
                    effects = {
                        colorControls(brightness = 0.15f, saturation = 0.72f)
                        blur(9.dp.toPx())
                        lens(16.dp.toPx(), 32.dp.toPx())
                    },
                    shadow = null,
                    highlight = { Highlight.Default.copy(alpha = 0.50f) },
                    onDrawSurface = {
                        drawRect(Color(0xFFEEF1F8).copy(alpha = 0.28f))
                    }
                )
                .animateContentSize(
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f)
                )
                .padding(horizontal = 15.dp, vertical = 14.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Button
                    ) {
                        expanded = !expanded
                        onExpandedChanged(expanded)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .padding(end = 10.dp)
                        .size(width = 5.dp, height = 23.dp)
                        .clip(RoundedRectangle(3.dp))
                        .background(Color(accentColor))
                )
                BasicText(
                    title,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(textPrimary, 16.sp, FontWeight.Bold)
                )
                BasicText(
                    if (expanded) "⌃" else "⌄",
                    modifier = Modifier
                        .widthIn(min = 32.dp)
                        .padding(bottom = if (expanded) 0.dp else 4.dp),
                    style = TextStyle(Color(accentColor), 20.sp, FontWeight.Bold)
                )
            }

            if (expanded) {
                val roomContentModifier = if (roomRows.size > 4) {
                    Modifier
                        .padding(top = 12.dp)
                        .height(188.dp)
                        .verticalScroll(rememberScrollState())
                } else {
                    Modifier.padding(top = 12.dp)
                }
                Column(
                    roomContentModifier,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    roomRows.forEach { pair ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { room ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicText(
                                        room,
                                        style = TextStyle(textPrimary, 14.sp, FontWeight.Bold)
                                    )
                                }
                            }
                            if (pair.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Exports one page-aligned source for glass children. The custom image is center-cropped
 * against the whole window and only then translated into this embedded ComposeView.
 * This keeps the wallpaper continuous across the status area, page and bottom dock.
 */
@Composable
private fun PageAlignedBackdropSource(
    backdrop: LayerBackdrop,
    pageBackgroundImage: ImageBitmap?,
    pageBackgroundScrim: Int,
    pageGradient: Brush
) {
    val hostView = LocalView.current
    var hostOffsetInWindow by remember { mutableStateOf(IntOffset.Zero) }
    var windowSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val root = hostView.rootView
                val rootLocation = IntArray(2)
                root.getLocationInWindow(rootLocation)
                val position = coordinates.positionInWindow()
                val nextOffset = IntOffset(
                    (position.x - rootLocation[0]).roundToInt(),
                    (position.y - rootLocation[1]).roundToInt()
                )
                val nextSize = IntSize(root.width, root.height)
                if (hostOffsetInWindow != nextOffset) hostOffsetInWindow = nextOffset
                if (windowSize != nextSize) windowSize = nextSize
            }
            .layerBackdrop(backdrop)
    ) {
        if (pageBackgroundImage == null) {
            Box(Modifier.fillMaxSize().background(pageGradient))
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val viewportWidth = windowSize.width.takeIf { it > 0 } ?: size.width.roundToInt()
                val viewportHeight = windowSize.height.takeIf { it > 0 } ?: size.height.roundToInt()
                val scale = maxOf(
                    viewportWidth.toFloat() / pageBackgroundImage.width,
                    viewportHeight.toFloat() / pageBackgroundImage.height
                )
                val scaledWidth = (pageBackgroundImage.width * scale).roundToInt()
                val scaledHeight = (pageBackgroundImage.height * scale).roundToInt()
                val imageLeftInWindow = (viewportWidth - scaledWidth) / 2
                val imageTopInWindow = (viewportHeight - scaledHeight) / 2
                drawImage(
                    image = pageBackgroundImage,
                    dstOffset = IntOffset(
                        imageLeftInWindow - hostOffsetInWindow.x,
                        imageTopInWindow - hostOffsetInWindow.y
                    ),
                    dstSize = IntSize(scaledWidth, scaledHeight),
                    filterQuality = FilterQuality.Medium
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(pageBackgroundScrim))
            )
        }
    }
}

@Composable
private fun ExamLiquidScrollPage(
    term: String,
    records: List<RemoteExam>,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int
) {
    val backdrop = rememberLayerBackdrop()
    val pageBackgroundImage = remember(pageBackgroundBitmap) {
        pageBackgroundBitmap?.asImageBitmap()
    }
    val secondaryFontWeight = if (pageBackgroundImage != null) FontWeight.Bold else FontWeight.Normal
    val textPrimary = Color(0xFF1C2230)
    val textSecondary = Color(0xFF666F85)
    val pageGradient = Brush.linearGradient(
        listOf(
            Color(0xFFF3F2F9),
            Color(0xFFF0F1F9),
            Color(0xFFEBEFF8),
            Color(0xFFE3EBF7),
            Color(0xFFD9E5F4)
        )
    )
    val sortedRecords = remember(records) {
        records.sortedWith(
            compareBy<RemoteExam>(
                { it.examWeek.toIntOrNull() ?: Int.MAX_VALUE },
                { it.examWeekday.toIntOrNull() ?: Int.MAX_VALUE },
                { Regex("\\d+").find(it.examSessions)?.value?.toIntOrNull() ?: Int.MAX_VALUE }
            )
        )
    }

    Box(Modifier.fillMaxSize()) {
        PageAlignedBackdropSource(
            backdrop = backdrop,
            pageBackgroundImage = pageBackgroundImage,
            pageBackgroundScrim = pageBackgroundScrim,
            pageGradient = pageGradient
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 28.dp)
        ) {
            BasicText(
                "考试安排",
                modifier = Modifier.padding(bottom = 7.dp),
                style = TextStyle(textPrimary, 28.sp, FontWeight.Bold)
            )
            BasicText(
                "$term 学期 · ${records.size} 门考试",
                modifier = Modifier.padding(bottom = 18.dp),
                style = TextStyle(textSecondary, 13.sp, secondaryFontWeight)
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sortedRecords.forEach { exam ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedRectangle(20.dp) },
                                effects = {
                                    vibrancy()
                                    lens(16.dp.toPx(), 32.dp.toPx())
                                }
                            )
                            .padding(horizontal = 18.dp, vertical = 17.dp)
                    ) {
                        BasicText(
                            exam.courseName.ifBlank { "未命名考试" },
                            modifier = Modifier.padding(bottom = 15.dp),
                            style = TextStyle(textPrimary, 18.sp, FontWeight.Bold)
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 13.dp)
                        ) {
                            ExamDetail(
                                label = "考试周数",
                                value = exam.examWeek.takeIf { it.isNotBlank() }?.let { "第${it}周" } ?: "-",
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                secondaryFontWeight = secondaryFontWeight,
                                modifier = Modifier.weight(1f)
                            )
                            ExamDetail(
                                label = "考试星期",
                                value = exam.examWeekday,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                secondaryFontWeight = secondaryFontWeight,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(Modifier.fillMaxWidth()) {
                            ExamDetail(
                                label = "考试节次",
                                value = exam.examSessions.takeIf { it.isNotBlank() }?.let { "${it}节" } ?: "-",
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                secondaryFontWeight = secondaryFontWeight,
                                modifier = Modifier.weight(1f)
                            )
                            ExamDetail(
                                label = "考试教室",
                                value = exam.classroom,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                secondaryFontWeight = secondaryFontWeight,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamDetail(
    label: String,
    value: String,
    textPrimary: Color,
    textSecondary: Color,
    secondaryFontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        BasicText(
            label,
            modifier = Modifier.padding(bottom = 5.dp),
            style = TextStyle(textSecondary, 12.sp, secondaryFontWeight)
        )
        BasicText(
            value.ifBlank { "-" },
            style = TextStyle(textPrimary, 15.sp, FontWeight.Bold)
        )
    }
}

@Composable
private fun ScoreLiquidScrollPage(
    result: RemoteScoreResult,
    scoreColors: List<Int>,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    onTermClick: () -> Unit,
    onScoreClick: (RemoteScore) -> Unit,
    onExport: () -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    val pageBackgroundImage = remember(pageBackgroundBitmap) {
        pageBackgroundBitmap?.asImageBitmap()
    }
    val secondaryFontWeight = if (pageBackgroundImage != null) FontWeight.Bold else FontWeight.Normal
    val scoreMetricFontWeight = if (pageBackgroundImage != null) FontWeight.ExtraBold else FontWeight.Bold
    val textPrimary = Color(0xFF1C2230)
    val textSecondary = Color(0xFF666F85)
    val pageGradient = Brush.linearGradient(
        listOf(
            Color(0xFFF3F2F9),
            Color(0xFFF0F1F9),
            Color(0xFFEBEFF8),
            Color(0xFFE3EBF7),
            Color(0xFFD9E5F4)
        )
    )

    Box(Modifier.fillMaxSize()) {
        // This is the actual source sampled by every card and by the export button.
        PageAlignedBackdropSource(
            backdrop = backdrop,
            pageBackgroundImage = pageBackgroundImage,
            pageBackgroundScrim = pageBackgroundScrim,
            pageGradient = pageGradient
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 96.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    "成绩",
                    style = TextStyle(textPrimary, 28.sp, FontWeight.Bold)
                )
                Row(
                    Modifier
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedRectangle(14.dp) },
                            effects = {
                                vibrancy()
                                blur(4.dp.toPx())
                                lens(8.dp.toPx(), 16.dp.toPx())
                            },
                            shadow = null,
                            highlight = { Highlight.Default.copy(alpha = 0.46f) },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = 0.16f))
                            }
                        )
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Button,
                            onClick = onTermClick
                        )
                        .padding(start = 11.dp, top = 7.dp, end = 9.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(result.term, style = TextStyle(textPrimary, 12.sp))
                    BasicText(
                        "⌄",
                        modifier = Modifier.padding(start = 5.dp, bottom = 2.dp),
                        style = TextStyle(textSecondary, 15.sp, secondaryFontWeight)
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(24.dp) },
                        effects = {
                            vibrancy()
                            blur(5.dp.toPx())
                            lens(12.dp.toPx(), 24.dp.toPx())
                        },
                        shadow = null,
                        highlight = { Highlight.Default.copy(alpha = 0.50f) },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 0.18f))
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreMetric(
                    label = "平均成绩",
                    value = result.averageScore,
                    valueColor = Color(0xFFF56C7E),
                    textSecondary = textSecondary,
                    secondaryFontWeight = secondaryFontWeight,
                    valueFontWeight = scoreMetricFontWeight
                )
                ScoreMetric(
                    label = "平均绩点",
                    value = result.averageCreditGpa,
                    valueColor = Color(0xFF838CC7),
                    textSecondary = textSecondary,
                    secondaryFontWeight = secondaryFontWeight,
                    valueFontWeight = scoreMetricFontWeight
                )
                ScoreMetric(
                    label = "总学分",
                    value = result.totalCredits,
                    valueColor = Color(0xFF324099),
                    textSecondary = textSecondary,
                    secondaryFontWeight = secondaryFontWeight,
                    valueFontWeight = scoreMetricFontWeight
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                result.records.forEachIndexed { index, record ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedRectangle(20.dp) },
                                effects = {
                                    vibrancy()
                                    lens(16.dp.toPx(), 32.dp.toPx())
                                }
                            )
                            .padding(horizontal = 17.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            BasicText(
                                record.courseName.ifBlank { "未命名课程" },
                                modifier = Modifier.padding(bottom = 7.dp),
                                style = TextStyle(textPrimary, 16.sp, FontWeight.Bold)
                            )
                            val details = buildList {
                                if (record.courseCode.isNotBlank()) add(record.courseCode)
                                if (record.credit.isNotBlank()) add("${record.credit} 学分")
                            }.joinToString("  ·  ")
                            BasicText(
                                details.ifBlank { "课程成绩" },
                                style = TextStyle(textSecondary, 12.sp, secondaryFontWeight)
                            )
                        }
                        Box(
                            Modifier
                                .size(width = 58.dp, height = 48.dp)
                                .clickable(
                                    interactionSource = null,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = { onScoreClick(record) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                record.score.ifBlank { "-" },
                                style = TextStyle(
                                    Color(scoreColors.getOrElse(index) { 0xFF324099.toInt() }),
                                    22.sp,
                                    FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 14.dp)
                .size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            SurfaceLiquidExportButton(backdrop = backdrop, onClick = onExport)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ScoreMetric(
    label: String,
    value: String,
    valueColor: Color,
    textSecondary: Color,
    secondaryFontWeight: FontWeight,
    valueFontWeight: FontWeight
) {
    Column(
        Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            value.ifBlank { "-" },
            modifier = Modifier.padding(bottom = 5.dp),
            style = TextStyle(valueColor, 21.sp, valueFontWeight)
        )
        BasicText(label, style = TextStyle(textSecondary, 11.sp, secondaryFontWeight))
    }
}

@Composable
private fun SurfaceLiquidExportButton(
    backdrop: com.kyant.backdrop.Backdrop,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.07f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "scoreExportLiquidScale"
    )
    Box(
        Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                highlight = { Highlight.Default.copy(alpha = 0.72f) },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.30f)) }
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = "保存成绩图片" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(24.dp)) {
            val color = Color(0xFF0088FF)
            val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            val cx = size.width / 2f
            drawLine(color, Offset(cx, size.height * 0.13f), Offset(cx, size.height * 0.62f), stroke.width, stroke.cap)
            drawLine(color, Offset(cx, size.height * 0.62f), Offset(size.width * 0.31f, size.height * 0.43f), stroke.width, stroke.cap)
            drawLine(color, Offset(cx, size.height * 0.62f), Offset(size.width * 0.69f, size.height * 0.43f), stroke.width, stroke.cap)
            drawLine(color, Offset(size.width * 0.20f, size.height * 0.84f), Offset(size.width * 0.80f, size.height * 0.84f), stroke.width, stroke.cap)
        }
    }
}
