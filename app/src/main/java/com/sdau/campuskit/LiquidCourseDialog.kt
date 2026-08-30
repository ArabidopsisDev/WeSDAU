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
/** Course details and editing form. */
internal class LiquidCourseDialogView(
    context: Context,
    private var pageSnapshot: Bitmap?,
    courseName: String,
    room: String,
    teacher: String,
    slotText: String,
    weeks: String,
    canEdit: Boolean,
    onSave: (name: String, room: String, teacher: String, weeks: String) -> Unit,
    onDismiss: () -> Unit
) : FrameLayout(context) {
    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        addView(
            composeHostView(context) {
                LiquidCourseDialog(
                    pageSnapshot = pageSnapshot,
                    initialCourseName = courseName,
                    initialRoom = room,
                    initialTeacher = teacher,
                    slotText = slotText,
                    initialWeeks = weeks,
                    canEdit = canEdit,
                    onSave = onSave,
                    onDismiss = onDismiss
                )
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

private enum class CourseDialogIcon { EDIT, SAVE }

@Composable
private fun LiquidCourseDialog(
    pageSnapshot: Bitmap?,
    initialCourseName: String,
    initialRoom: String,
    initialTeacher: String,
    slotText: String,
    initialWeeks: String,
    canEdit: Boolean,
    onSave: (name: String, room: String, teacher: String, weeks: String) -> Unit,
    onDismiss: () -> Unit
) {
    val contentColor = Color(0xFF171923)
    val secondaryColor = contentColor.copy(alpha = 0.66f)
    val accentColor = Color(0xFF0088FF)
    val containerColor = Color(0xFFF2F4F8).copy(alpha = 0.42f)
    val dimColor = Color(0xFF29293A).copy(alpha = 0.23f)
    val snapshotImage = remember(pageSnapshot) { pageSnapshot?.asImageBitmap() }
    val backdrop = rememberLayerBackdrop()
    var editing by remember { mutableStateOf(false) }
    var courseName by remember(initialCourseName) { mutableStateOf(initialCourseName) }
    var room by remember(initialRoom) { mutableStateOf(initialRoom) }
    var teacher by remember(initialTeacher) { mutableStateOf(initialTeacher) }
    var weeks by remember(initialWeeks) { mutableStateOf(initialWeeks) }

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
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
        )
        Box(
            Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .widthIn(max = 372.dp)
                    .clip(RoundedRectangle(28.dp))
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(28.dp) },
                        effects = {
                            vibrancy()
                            colorControls(brightness = 0.14f, saturation = 0.80f)
                            blur(18.dp.toPx())
                            lens(12.dp.toPx(), 24.dp.toPx(), depthEffect = true)
                        },
                        shadow = null,
                        highlight = { Highlight.Default.copy(alpha = 0.58f) },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .clickable(interactionSource = null, indication = null, onClick = {})
                    .animateContentSize()
            ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 20.dp, end = 18.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 10.dp)) {
                    BasicText(
                        if (editing) "修改课程" else "课程详情",
                        style = TextStyle(secondaryColor, 12.sp, FontWeight.Medium)
                    )
                    BasicText(
                        courseName.ifBlank { "未命名课程" },
                        modifier = Modifier.padding(top = 4.dp),
                        style = TextStyle(contentColor, 20.sp, FontWeight.SemiBold)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!editing && canEdit) {
                        CourseLiquidIconButton(
                            backdrop = backdrop,
                            icon = CourseDialogIcon.EDIT,
                            contentDescription = "修改课程",
                            onClick = { editing = true }
                        )
                    }
                    if (editing) {
                        CourseLiquidIconButton(
                            backdrop = backdrop,
                            icon = CourseDialogIcon.SAVE,
                            contentDescription = "保存修改",
                            onClick = { onSave(courseName, room, teacher, weeks) }
                        )
                    }
                }
            }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 22.dp)
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                if (editing) {
                    CourseLiquidTextField(
                        label = "课程名",
                        value = courseName,
                        onValueChange = { courseName = it }
                    )
                    CourseLiquidTextField(
                        label = "地点",
                        value = room,
                        onValueChange = { room = it }
                    )
                    CourseLiquidTextField(
                        label = "教师",
                        value = teacher,
                        onValueChange = { teacher = it }
                    )
                    CourseLiquidTextField(
                        label = "周数",
                        value = weeks,
                        onValueChange = { weeks = it }
                    )
                } else {
                    CourseDetailLine(
                        label = "地点",
                        value = "@${room.ifBlank { "-" }}",
                        iconRes = R.drawable.ic_detail_location,
                        contentColor = contentColor,
                        secondaryColor = secondaryColor
                    )
                    CourseDetailLine(
                        label = "教师",
                        value = teacher.ifBlank { "-" },
                        iconRes = R.drawable.ic_detail_teacher,
                        contentColor = contentColor,
                        secondaryColor = secondaryColor
                    )
                    CourseDetailLine(
                        label = "节次",
                        value = slotText,
                        iconRes = R.drawable.ic_detail_time,
                        contentColor = contentColor,
                        secondaryColor = secondaryColor
                    )
                    CourseDetailLine(
                        label = "周数",
                        value = weeks.ifBlank { "-" },
                        iconRes = R.drawable.ic_detail_week,
                        contentColor = contentColor,
                        secondaryColor = secondaryColor
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun CourseDetailLine(
    label: String,
    value: String,
    iconRes: Int,
    contentColor: Color,
    secondaryColor: Color
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(Color(0xFF0088FF))
        )
        BasicText(
            label,
            modifier = Modifier.padding(start = 10.dp).width(42.dp),
            style = TextStyle(secondaryColor, 12.sp, FontWeight.Bold)
        )
        BasicText(
            value,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            style = TextStyle(contentColor, 15.sp, FontWeight.Medium)
        )
    }
}

@Composable
private fun CourseLiquidTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val contentColor = Color(0xFF171923)
    val accentColor = Color(0xFF0088FF)
    val fieldShape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            // The dialog shell has already sampled and blurred the page. Sampling the
            // root backdrop again here would reveal a clearer copy of the original
            // timetable inside every field, so fields only tint the blurred shell.
            .clip(fieldShape)
            .background(Color(0xFFF2F4F8).copy(alpha = 0.22f), fieldShape)
            .border(1.dp, Color.White.copy(alpha = 0.68f), fieldShape)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        BasicText(label, style = TextStyle(contentColor.copy(alpha = 0.62f), 10.sp, FontWeight.Medium))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            textStyle = TextStyle(contentColor, 15.sp, FontWeight.Medium),
            cursorBrush = SolidColor(accentColor),
            singleLine = true
        )
    }
}

@Composable
private fun CourseLiquidIconButton(
    backdrop: com.kyant.backdrop.Backdrop,
    icon: CourseDialogIcon,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.07f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "courseDialogIconScale"
    )
    val accentColor = Color(0xFF0088FF)
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    colorControls(brightness = 0.14f, saturation = 0.84f)
                    blur(8.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                highlight = { Highlight.Default.copy(alpha = 0.72f) },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.38f)) }
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        when (icon) {
            CourseDialogIcon.EDIT -> Image(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(accentColor)
            )
            CourseDialogIcon.SAVE -> Image(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(accentColor)
            )
        }
    }
}


