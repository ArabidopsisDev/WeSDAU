package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
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
/** Exam page and empty-room result cards. */

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
    pageBackgroundScrim: Int,
    textPalette: ScheduleTextPalette
): View = composeHostView(context) {
    ExamLiquidScrollPage(
        term = term,
        records = records,
        pageBackgroundBitmap = pageBackgroundBitmap,
        pageBackgroundScrim = pageBackgroundScrim,
        textPalette = textPalette
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
    textPalette: ScheduleTextPalette,
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
        textPalette = textPalette,
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
    textPalette: ScheduleTextPalette,
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
    val textPrimary = Color(textPalette.primary)
    val textShadow = scheduleTextShadow(textPalette)
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
                    style = TextStyle(
                        color = textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = textShadow
                    )
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
                                        style = TextStyle(
                                            color = textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            shadow = textShadow
                                        )
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

@Composable
private fun ExamLiquidScrollPage(
    term: String,
    records: List<RemoteExam>,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    textPalette: ScheduleTextPalette
) {
    val backdrop = rememberLayerBackdrop()
    val pageBackgroundImage = remember(pageBackgroundBitmap) {
        pageBackgroundBitmap?.asImageBitmap()
    }
    val secondaryFontWeight = if (textPalette.adaptive) FontWeight.ExtraBold else FontWeight.Normal
    val courseNameFontWeight = if (textPalette.adaptive) FontWeight.ExtraBold else FontWeight.Bold
    val textPrimary = Color(textPalette.primary)
    val textSecondary = Color(textPalette.secondary)
    val textShadow = scheduleTextShadow(textPalette)
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
                style = TextStyle(
                    color = textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = textShadow
                )
            )
            BasicText(
                "$term 学期 · ${records.size} 门考试",
                modifier = Modifier.padding(bottom = 18.dp),
                style = TextStyle(
                    color = textSecondary,
                    fontSize = 13.sp,
                    fontWeight = secondaryFontWeight,
                    shadow = textShadow
                )
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
                            style = TextStyle(
                                color = textPrimary,
                                fontSize = 18.sp,
                                fontWeight = courseNameFontWeight,
                                shadow = textShadow
                            )
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
                                textShadow = textShadow,
                                modifier = Modifier.weight(1f)
                            )
                            ExamDetail(
                                label = "考试星期",
                                value = exam.examWeekday,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                secondaryFontWeight = secondaryFontWeight,
                                textShadow = textShadow,
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
                                textShadow = textShadow,
                                modifier = Modifier.weight(1f)
                            )
                            ExamDetail(
                                label = "考试教室",
                                value = exam.classroom,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                secondaryFontWeight = secondaryFontWeight,
                                textShadow = textShadow,
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
    textShadow: Shadow?,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        BasicText(
            label,
            modifier = Modifier.padding(bottom = 5.dp),
            style = TextStyle(
                color = textSecondary,
                fontSize = 12.sp,
                fontWeight = secondaryFontWeight,
                shadow = textShadow
            )
        )
        BasicText(
            value.ifBlank { "-" },
            style = TextStyle(
                color = textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                shadow = textShadow
            )
        )
    }
}

private fun scheduleTextShadow(textPalette: ScheduleTextPalette): Shadow? =
    if (textPalette.adaptive) {
        Shadow(
            color = Color(textPalette.halo),
            offset = Offset.Zero,
            blurRadius = 1.6f
        )
    } else null
