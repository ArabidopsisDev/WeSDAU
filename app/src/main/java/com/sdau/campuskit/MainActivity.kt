package com.sdau.campuskit

import android.Manifest
import android.app.AlarmManager
import android.app.DownloadManager
import android.content.Context
import android.content.ContentValues
import android.content.ActivityNotFoundException
import android.content.res.ColorStateList
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.VelocityTracker
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.net.Uri
import android.provider.Settings
import android.provider.MediaStore
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import android.widget.PopupWindow
import android.widget.ProgressBar
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.concurrent.Executors
import java.nio.charset.StandardCharsets
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

class MainActivity : android.app.Activity() {
    private class EmptyRoomPriorityScrollView(context: Context) : ScrollView(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val density = resources.displayMetrics.density
        private val scrollThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(126, 91, 108, 165)
        }
        private var downY = 0f
        private var lastY = 0f

        init {
            isVerticalScrollBarEnabled = false
            isSmoothScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.y
                    lastY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = lastY - event.y
                    if (kotlin.math.abs(event.y - downY) > touchSlop && deltaY != 0f) {
                        val direction = if (deltaY > 0f) 1 else -1
                        parent?.requestDisallowInterceptTouchEvent(canScrollVertically(direction))
                    }
                    lastY = event.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            return super.dispatchTouchEvent(event)
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            val content = getChildAt(0) ?: return
            val viewportHeight = (height - paddingTop - paddingBottom).coerceAtLeast(1)
            val contentHeight = content.height
            if (contentHeight <= viewportHeight) return

            val thumbWidth = 2.5f * density
            val thumbMargin = 3f * density
            val minimumThumbHeight = 22f * density
            val thumbHeight = kotlin.math.max(
                minimumThumbHeight,
                viewportHeight.toFloat() * viewportHeight / contentHeight
            ).coerceAtMost(viewportHeight.toFloat())
            val scrollRange = (contentHeight - viewportHeight).coerceAtLeast(1)
            val travelRange = viewportHeight - thumbHeight
            val thumbTop = scrollY + paddingTop +
                travelRange * (scrollY.toFloat() / scrollRange).coerceIn(0f, 1f)
            val thumbRight = scrollX + width - thumbMargin
            val cornerRadius = thumbWidth / 2f
            canvas.drawRoundRect(
                thumbRight - thumbWidth,
                thumbTop,
                thumbRight,
                thumbTop + thumbHeight,
                cornerRadius,
                cornerRadius,
                scrollThumbPaint
            )
        }
    }

    private lateinit var pageHost: FrameLayout
    private lateinit var studentIdBox: TextInputLayout
    private lateinit var passwordBox: TextInputLayout
    private lateinit var studentId: TextInputEditText
    private lateinit var password: TextInputEditText
    private lateinit var semesterInput: MaterialAutoCompleteTextView
    private var loginMode = LoginMode.PERSONAL
    private var publicCollegeSelection = ""
    private var publicGradeSelection = ""
    private var publicMajorSelection = ""
    private var publicClassSelection = ""
    private var viewingPublicSchedule = false
    private var publicScheduleCourses: List<Course> = emptyList()
    private var publicScheduleTerm = ""
    private var publicScheduleLabel = ""
    private var publicScheduleClassName = ""
    private var publicSyncRunning = false
    private val publicScheduleMemoryCache =
        java.util.concurrent.ConcurrentHashMap<String, List<RemotePublicCourse>>()
    private var onLoginPage = false
    private lateinit var publicCollegeInput: MaterialAutoCompleteTextView
    private lateinit var publicGradeInput: MaterialAutoCompleteTextView
    private lateinit var publicMajorInput: MaterialAutoCompleteTextView
    private lateinit var publicClassInput: MaterialAutoCompleteTextView
    private var loginButton: MaterialButton? = null
    private var loginStatus: TextView? = null
    private var scheduleDate: TextView? = null
    private var scheduleWeek: TextView? = null
    private var scheduleGrid: ScheduleGridView? = null
    private var mainSectionHost: FrameLayout? = null
    private var currentMainSection = 0
    private var mainSectionTransitionGeneration = 0
    private var detailOverlay: View? = null
    private var editorOverlay: View? = null
    private var modeOverlay: View? = null
    private var semesterOverlay: View? = null
    private var scoreTermOverlay: View? = null
    private var scoreDetailOverlay: View? = null
    private var emptyRoomFilterOverlay: View? = null
    private var publicOptionOverlay: View? = null
    private var shareOverlay: View? = null
    private var updateOverlay: View? = null
    private var pendingTestNotification = false
    private var pendingPushEnable = false
    private var pushButton: ImageButton? = null
    private var bottomNavigation: LiquidGlassNavigationView? = null
    private var scoresLoading = false
    private var scoreExporting = false
    private var scheduleExporting = false
    private var scoreLoadError: String? = null
    private var examsLoading = false
    private var examLoadError: String? = null
    private var emptyRoomsLoading = false
    private var emptyRoomLoadError: String? = null
    private var emptyRoomResult: RemoteEmptyRoomResult? = null
    private var emptyRoomRequestGeneration = 0
    private var emptyRoomCampus = "泮河校区"
    private var emptyRoomWeek = 1
    private var emptyRoomWeekday = Calendar.getInstance().let {
        val day = it.get(Calendar.DAY_OF_WEEK)
        if (day == Calendar.SUNDAY) 7 else day - 1
    }
    private var emptyRoomSectionCode = "0102"
    private var emptyRoomQueryExpanded = true
    private val collapsedEmptyRoomGroups = mutableSetOf<String>()
    private var pushEnabled = false
    private var scheduleMode = ScheduleMode.SPRING
    private var currentWeek = 1
    private var pendingApkUrl = APK_URL
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val updateExecutor = Executors.newSingleThreadExecutor()
    @Suppress("DEPRECATION")
    private val currentVersionCode: Int by lazy {
        packageManager.getPackageInfo(packageName, 0).let { info ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
            else info.versionCode
        }
    }
    @Suppress("DEPRECATION")
    private val appDisplayVersion: String by lazy {
        val installedName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty().ifBlank {
            currentVersionCode.toString()
        }
        if (installedName.startsWith("V", ignoreCase = true)) installedName else "V$installedName"
    }
    private data class RemoteUpdate(val code: Int, val name: String, val changelog: String, val url: String)
    private data class ExamCache(val term: String, val records: List<RemoteExam>)
    private data class EmptyRoomGroup(val title: String, val accent: Int, val rooms: List<String>)
    private enum class LoginMode { PERSONAL, PUBLIC }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        scheduleMode = loadScheduleMode()
        pushEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_PUSH_ENABLED, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
        }
        pageHost = FrameLayout(this).apply { setBackgroundColor(PAGE_BACKGROUND) }
        setContentView(pageHost)
        startPublicScheduleSyncIfNeeded(inferredCurrentTerm())
        if (hasLocalCourseCache()) showSchedulePage() else showLoginPage(false)
        checkForOnlineUpdate()
    }

    private fun checkForOnlineUpdate() {
        updateExecutor.execute {
            try {
                val update = readRemoteUpdate() ?: return@execute
                if (update.code <= currentVersionCode) return@execute
                val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                if (preferences.getInt(KEY_UPDATE_STARTED_CODE, 0) >= update.code) return@execute
                pendingApkUrl = update.url
                runOnUiThread {
                    showUpdateDialog(update)
                }
            } catch (_: Exception) {
                // 网络不可用时保持离线使用，不打扰课表页面。
            }
        }
    }

    private fun readRemoteUpdate(): RemoteUpdate? {
        val connection = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000; readTimeout = 5000; requestMethod = "GET"; useCaches = false
        }
        val remoteText = connection.inputStream.bufferedReader().use { it.readText() }.trim()
        connection.disconnect()
        val json = runCatching { JSONObject(remoteText) }.getOrNull()
        if (json == null) return remoteText.trim('"').toIntOrNull()?.let { RemoteUpdate(it, "", "", APK_URL) }
        val code = json.optInt("latestVersionCode", json.optInt("versionCode", json.optInt("version", 0)))
        val name = json.optString("latestVersionName", json.optString("versionName", ""))
        val url = json.optString("downloadUrl", APK_URL).ifBlank { APK_URL }
        val changelog = when {
            json.optJSONArray("changelog") != null -> {
                val items = json.optJSONArray("changelog")!!
                (0 until items.length()).joinToString("\n") { "• ${items.optString(it)}" }
            }
            else -> json.optString("changelog", "")
        }
        return RemoteUpdate(code, name, changelog, url)
    }

    private fun downloadLatestApk(
        apkUrl: String,
        remoteVersion: Int,
        installExisting: Boolean = false,
        notifyStarted: Boolean = false
    ) {
        try {
            val existing = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                UPDATE_FILE_NAME
            )
            if (installExisting && existing.isFile && existing.length() > 0L) {
                installDownloadedApk(existing)
                return
            }
            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("WeSDAU课程表更新")
                setDescription("正在下载最新安装包")
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, UPDATE_FILE_NAME)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putInt(KEY_UPDATE_STARTED_CODE, remoteVersion).apply()
            if (notifyStarted) {
                Toast.makeText(this, "检测到新版本，请在右上角“检查更新”处安装", Toast.LENGTH_LONG).show()
            }
        } catch (error: Exception) {
            // 下次启动时继续检查并重试。
        }
    }

    private fun requestInstallPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (packageManager.canRequestPackageInstalls()) return
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            Toast.makeText(this, "请允许安装应用，返回后再次点击检查更新", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "请在系统设置中允许安装应用", Toast.LENGTH_LONG).show()
        }
    }

    private fun installDownloadedApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (error: Exception) {
            Toast.makeText(this, "无法打开安装包：${error.message ?: "请手动在下载目录安装"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showUpdateDialog(update: RemoteUpdate) {
        if (updateOverlay != null) return
        pendingApkUrl = update.url
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(142, 15, 21, 36))
            isClickable = true
            setOnClickListener { hideUpdateDialog() }
        }
        val card = surfaceCard(dp(26f).toFloat()).apply {
            cardElevation = dp(5).toFloat()
            strokeWidth = 0
            setCardBackgroundColor(Color.rgb(249, 251, 255))
            setOnClickListener { }
        }
        val body = verticalLayout().apply { setPadding(dp(21), dp(20), dp(21), dp(18)) }
        val versionName = update.name.ifBlank { "V${update.code}" }
        val changelogText = update.changelog.ifBlank { "本次更新包含体验优化与问题修复。" }
        val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15f * resources.displayMetrics.density * resources.configuration.fontScale
            typeface = Typeface.DEFAULT
        }
        val longestTextWidth = (changelogText.lines() + versionName + "发现新版本")
            .maxOfOrNull { measurePaint.measureText(it) }
            ?: 0f
        val maximumDialogWidth = minOf(dp(330), resources.displayMetrics.widthPixels - dp(32))
        val minimumDialogWidth = minOf(dp(268), maximumDialogWidth)
        val dialogWidth = (longestTextWidth + dp(50)).toInt().coerceIn(minimumDialogWidth, maximumDialogWidth)
        val availableLogWidth = (dialogWidth - dp(48)).coerceAtLeast(dp(160))
        val estimatedLogLines = changelogText.lines().sumOf { line ->
            kotlin.math.ceil(measurePaint.measureText(line).coerceAtLeast(1f) / availableLogWidth).toInt().coerceAtLeast(1)
        }
        val changelogHeight = (estimatedLogLines * dp(27) + dp(8)).coerceIn(dp(82), dp(220))
        body.addView(text("发现新版本", 23f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(7)))
        body.addView(text(versionName, 13f, PRIMARY_DARK, Typeface.BOLD), spacedParams(dp(18)))
        body.addView(text("更新内容", 13f, TEXT_SECONDARY, Typeface.BOLD), spacedParams(dp(9)))

        val changelogScroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = true
            setBackgroundColor(Color.TRANSPARENT)
        }
        changelogScroll.addView(text(
            changelogText,
            15f, TEXT_PRIMARY, Typeface.NORMAL
        ).apply {
            setLineSpacing(dp(6).toFloat(), 1f)
            setPadding(0, 0, dp(6), dp(4))
        }, FrameLayout.LayoutParams(-1, -2))
        body.addView(changelogScroll, LinearLayout.LayoutParams(-1, -2).apply {
            height = changelogHeight
            bottomMargin = dp(18)
        })

        val actions = horizontalLayout().apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(MaterialButton(this).apply {
            text = "稍后"
            textSize = 14f
            isAllCaps = false
            cornerRadius = dp(15)
            insetTop = 0
            insetBottom = 0
            setTextColor(TEXT_SECONDARY)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(238, 242, 249))
            setOnClickListener { hideUpdateDialog() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        actions.addView(MaterialButton(this).apply {
            text = "立即更新"
            textSize = 14f
            isAllCaps = false
            cornerRadius = dp(15)
            insetTop = 0
            insetBottom = 0
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(PRIMARY_DARK)
            setOnClickListener {
                requestInstallPermissionIfNeeded()
                downloadLatestApk(update.url, update.code, installExisting = true)
                hideUpdateDialog()
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
        body.addView(actions, matchWrapParams())
        card.addView(body)
        overlay.addView(card, FrameLayout.LayoutParams(dialogWidth, -2, Gravity.CENTER))
        pageHost.addView(overlay, matchParentParams())
        updateOverlay = overlay
        overlay.alpha = 0f
        card.scaleX = .94f
        card.scaleY = .94f
        overlay.animate().alpha(1f).setDuration(150).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(210).start()
    }

    private fun hideUpdateDialog() {
        val overlay = updateOverlay ?: return
        overlay.animate().alpha(0f).setDuration(140).withEndAction {
            pageHost.removeView(overlay)
            updateOverlay = null
        }.start()
    }

    private fun showLoginPage(animate: Boolean) {
        loginMode = LoginMode.PERSONAL
        viewingPublicSchedule = false
        publicScheduleCourses = emptyList()
        publicScheduleTerm = ""
        publicScheduleLabel = ""
        publicScheduleClassName = ""
        publicCollegeSelection = ""
        publicGradeSelection = ""
        publicMajorSelection = ""
        publicClassSelection = ""
        onLoginPage = true
        setSystemBars(PAGE_BACKGROUND)
        cancelSystemCourseReminder()
        emptyRoomRequestGeneration++
        emptyRoomsLoading = false
        emptyRoomLoadError = null
        emptyRoomResult = null
        bottomNavigation = null
        pushButton = null
        detailOverlay = null
        editorOverlay = null
        modeOverlay = null
        semesterOverlay = null
        scoreTermOverlay = null
        scoreDetailOverlay = null
        emptyRoomFilterOverlay = null
        publicOptionOverlay = null
        shareOverlay = null
        swapPage(buildLoginPage(), false, animate)
    }

    private fun showSchedulePage() {
        onLoginPage = false
        hideKeyboard()
        val account = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_ACCOUNT, "").orEmpty()
        if (account == "114514") {
            // 演示数据随版本代码更新，避免旧安装继续读取之前缓存的地点。
            saveCourseCache(sampleCourses())
            if (loadScoreCache() == null) saveScoreCache(sampleScoreResult(selectedScoreTerm()))
            saveExamCache(selectedTerm(), sampleExams())
        }
        currentWeek = weekForTerm(if (viewingPublicSchedule) publicScheduleTerm else selectedTerm())
        if (emptyRoomResult == null) syncEmptyRoomDefaultsToNow()
        currentMainSection = 0
        setSystemBars(GRADIENT_START)
        window.navigationBarColor = GRADIENT_END
        swapPage(buildSchedulePage(), true, true)
        updatePushButton()
    }

    private fun defaultScheduleMode(): ScheduleMode {
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        return if (month >= 9 || month <= 4) ScheduleMode.SPRING else ScheduleMode.SUMMER
    }

    private fun selectedTerm(): String = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        .getString(KEY_TERM, inferredCurrentTerm()) ?: inferredCurrentTerm()

    private fun activeScheduleTerm(): String = if (viewingPublicSchedule) publicScheduleTerm else selectedTerm()

    private fun activeScheduleCourses(): List<Course> = if (viewingPublicSchedule) publicScheduleCourses else loadCourseCache()

    private fun termStartDate(term: String): Calendar {
        val date = Calendar.getInstance()
        when (term) {
            OFFICIAL_TERM -> date.set(
                OFFICIAL_TERM_START_YEAR,
                OFFICIAL_TERM_START_MONTH,
                OFFICIAL_TERM_START_DAY,
                0, 0, 0
            )
            "2026-2027-2" -> date.set(2027, Calendar.FEBRUARY, 22, 0, 0, 0)
            else -> {
                val parts = term.split("-")
                val start = parts.firstOrNull()?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                if (parts.getOrNull(2) == "2") date.set(start + 1, Calendar.FEBRUARY, 1, 0, 0, 0)
                else date.set(start, Calendar.SEPTEMBER, 1, 0, 0, 0)
                while (date.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) date.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        date.set(Calendar.MILLISECOND, 0)
        return date
    }

    private fun weekForTerm(term: String): Int {
        val start = termStartDate(term)
        val today = Calendar.getInstance()
        start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0); start.set(Calendar.SECOND, 0)
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0)
        val days = ((today.timeInMillis - start.timeInMillis) / 86_400_000L).toInt()
        return if (days < 0) 0 else (days / 7 + 1).coerceIn(1, 20)
    }

    private fun inferredCurrentTerm(): String {
        val today = Calendar.getInstance()
        val year = today.get(Calendar.YEAR)
        val month = today.get(Calendar.MONTH) + 1
        val day = today.get(Calendar.DAY_OF_MONTH)
        return when {
            month > 7 || (month == 7 && day >= 20) -> "$year-${year + 1}-1"
            month > 2 || (month == 2 && day >= 16) -> "${year - 1}-$year-2"
            else -> "${year - 1}-$year-1"
        }
    }

    private fun nextTerm(term: String): String {
        val parts = term.split("-")
        if (parts.size != 3) return term
        val start = parts[0].toIntOrNull() ?: return term
        return if (parts[2] == "1") "${start}-${start + 1}-2" else "${start + 1}-${start + 2}-1"
    }

    private fun previousTerm(term: String): String {
        val parts = term.split("-")
        if (parts.size != 3) return term
        val start = parts[0].toIntOrNull() ?: return term
        return if (parts[2] == "2") "$start-${start + 1}-1" else "${start - 1}-$start-2"
    }

    private fun scoreTermOptions(): List<String> {
        val terms = mutableListOf<String>()
        var term = inferredCurrentTerm()
        repeat(8) {
            terms += term
            term = previousTerm(term)
        }
        return terms
    }

    private fun allScoreTerms(account: String): List<String> {
        val current = inferredCurrentTerm()
        val enrollmentYear = account.take(4).toIntOrNull()
            ?.takeIf { it in 2000..Calendar.getInstance().get(Calendar.YEAR) }
            ?: return scoreTermOptions().reversed()
        val result = mutableListOf<String>()
        var term = "$enrollmentYear-${enrollmentYear + 1}-1"
        while (termOrder(term) <= termOrder(current)) {
            result += term
            val next = nextTerm(term)
            if (next == term) break
            term = next
        }
        return result
    }

    private fun selectedScoreTerm(): String {
        val options = scoreTermOptions()
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_SCORE_TERM, options.first()).orEmpty()
            .takeIf { it in options }
            ?: options.first()
    }

    private fun semesterOptions(): Array<String> {
        val result = mutableListOf<String>()
        val current = inferredCurrentTerm()
        val base = OFFICIAL_TERM
        var term = if (termOrder(current) < termOrder(base)) current else base
        while (termOrder(term) <= termOrder(current)) {
            result += term
            val next = nextTerm(term)
            if (next == term) break
            term = next
        }
        return result.takeLast(8).toTypedArray()
    }

    private fun termOrder(term: String): Int {
        val parts = term.split("-")
        val start = parts.getOrNull(0)?.toIntOrNull() ?: return Int.MIN_VALUE
        val number = parts.getOrNull(2)?.toIntOrNull() ?: return 0
        return start * 2 + number - 1
    }

    private fun loadScheduleMode(): ScheduleMode {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_SCHEDULE_MODE, null)
            ?.let { runCatching { ScheduleMode.valueOf(it) }.getOrNull() }
            ?: defaultScheduleMode()
    }

    private fun setSystemBars(color: Int) {
        window.statusBarColor = color
        window.navigationBarColor = color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.navigationBarDividerColor = color
        }
        var flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility = flags
    }

    private fun swapPage(next: View, forward: Boolean, animate: Boolean) {
        val previous = pageHost.getChildAt(0)
        if (!animate || previous == null) {
            pageHost.removeAllViews()
            pageHost.addView(next, matchParentParams())
            return
        }
        val distance = dp(36).toFloat() * if (forward) 1f else -1f
        next.alpha = 0f
        next.translationX = distance
        pageHost.addView(next, matchParentParams())
        next.animate().alpha(1f).translationX(0f).setDuration(220).start()
        previous.animate().alpha(0f).translationX(-distance * 0.55f).setDuration(180)
            .withEndAction { pageHost.removeView(previous) }.start()
    }

    private fun buildLoginPage(): View {
        val scroll = ScheduleScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(PUBLIC_PAGE_BACKGROUND)
        }
        val viewport = verticalLayout().apply {
            gravity = Gravity.CENTER
            setPadding(dp(20), 0, dp(20), dp(24))
        }
        scroll.addView(viewport, FrameLayout.LayoutParams(-1, -2))

        val card = surfaceCard(dp(28f).toFloat()).apply {
            setCardBackgroundColor(PUBLIC_SURFACE)
            setStrokeColor(PUBLIC_CARD_OUTLINE)
            cardElevation = dp(2).toFloat()
        }
        val body = verticalLayout().apply { setPadding(dp(24), dp(24), dp(24), dp(22)) }
        body.addView(text("登录", 28f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(8)))

        val formHost = FrameLayout(this)
        val modeToggle = LoginModeToggle(this, loginMode) { nextMode, _ ->
            if (nextMode != loginMode) {
                val currentTerm = semesterInput.text?.toString().orEmpty()
                loginMode = nextMode
                if (nextMode == LoginMode.PUBLIC) {
                    startPublicScheduleSyncIfNeeded(currentTerm)
                }
                transitionLoginModeForm(
                    formHost,
                    buildLoginModeForm(nextMode),
                    nextMode == LoginMode.PUBLIC
                )
            }
        }
        body.addView(modeToggle, LinearLayout.LayoutParams(-1, dp(60)).apply {
            leftMargin = dp(16)
            rightMargin = dp(16)
            bottomMargin = dp(20)
        })
        formHost.addView(buildLoginModeForm(loginMode), FrameLayout.LayoutParams(-1, -2))
        body.addView(formHost, matchWrapParams())
        card.addView(body)
        viewport.addView(card, matchWrapParams())
        viewport.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val available = view.width - view.paddingLeft - view.paddingRight
            val width = minOf(available, dp(480))
            val params = card.layoutParams
            if (width > 0 && params.width != width) { params.width = width; card.layoutParams = params }
        }
        return scroll
    }

    private fun buildLoginModeForm(mode: LoginMode): View {
        val form = verticalLayout()
        if (mode == LoginMode.PERSONAL) {
            studentIdBox = inputBox("学号")
            studentId = input(InputType.TYPE_CLASS_NUMBER).apply {
                imeOptions = EditorInfo.IME_ACTION_NEXT
                val savedAccount = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(KEY_ACCOUNT, "").orEmpty()
                setText(if (savedAccount.isNotEmpty() && savedAccount.all { it.isDigit() }) savedAccount else "")
            }
            studentIdBox.addView(studentId)
            form.addView(studentIdBox, spacedParams(dp(14)))

            passwordBox = inputBox("密码")
            password = input(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply {
                imeOptions = EditorInfo.IME_ACTION_DONE
                setText(getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_PASSWORD, ""))
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) { attemptLogin(); true } else false
                }
            }
            passwordBox.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            passwordBox.addView(password)
            form.addView(passwordBox, spacedParams(dp(20)))

            val semesterBox = inputBox("学期")
            val semesterOptions = semesterOptions()
            semesterInput = MaterialAutoCompleteTextView(this).apply {
                val savedTerm = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_TERM, "")
                setText(if (savedTerm in semesterOptions) savedTerm else semesterOptions.first(), false)
                setTextColor(TEXT_PRIMARY)
                textSize = 16f
                inputType = InputType.TYPE_NULL
                isFocusable = false
                minHeight = dp(72)
                setPadding(dp(16), dp(16), dp(16), dp(6))
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { showSemesterPicker() }
            }
            semesterBox.addView(semesterInput)
            form.addView(semesterBox, spacedParams(dp(18)))
        } else {
            semesterInput = MaterialAutoCompleteTextView(this)
            val semesterOptions = semesterOptions().toList()
            val savedTerm = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_TERM, "")
            val selectedTerm = savedTerm?.takeIf { it in semesterOptions } ?: semesterOptions.first()
            semesterInput.setText(selectedTerm, false)
            form.addView(publicFormField("学期", semesterInput, semesterOptions, selectedTerm) {
                showSemesterPicker()
            }, spacedParams(dp(14)))
            buildPublicFilterFields(form, selectedTerm)
        }

        loginStatus = text("", 13f, ERROR, Typeface.NORMAL).apply {
            visibility = View.GONE
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        form.addView(loginStatus, spacedParams(dp(12)))

        val login = MaterialButton(this).apply {
            text = if (mode == LoginMode.PERSONAL) "进入个人课表" else "查询班级课表"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            isAllCaps = false
            cornerRadius = dp(if (mode == LoginMode.PERSONAL) 18 else 23)
            insetTop = 0
            insetBottom = 0
            minimumHeight = dp(if (mode == LoginMode.PERSONAL) 56 else 46)
            backgroundTintList = buttonColors()
            setOnClickListener {
                if (mode == LoginMode.PERSONAL) attemptLogin() else attemptPublicScheduleLookup()
            }
        }
        if (mode == LoginMode.PUBLIC && !hasPublicScheduleCache(semesterInput.text?.toString().orEmpty())) {
            login.isEnabled = false
            login.text = "正在准备全校课表…"
        }
        loginButton = login
        form.addView(login, LinearLayout.LayoutParams(-1, -2).apply {
            if (mode == LoginMode.PUBLIC) topMargin = dp(6)
        })
        return form
    }

    private fun transitionLoginModeForm(host: FrameLayout, next: View, forward: Boolean) {
        val previous = host.getChildAt(0)
        if (previous == null || host.width <= 0) {
            host.removeAllViews()
            host.addView(next, FrameLayout.LayoutParams(-1, -2))
            return
        }

        next.measure(
            View.MeasureSpec.makeMeasureSpec(host.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val startHeight = host.height
        val targetHeight = next.measuredHeight
        val distance = dp(24).toFloat() * if (forward) 1f else -1f
        next.alpha = 0f
        next.translationX = distance
        host.addView(next, FrameLayout.LayoutParams(-1, -2))
        host.isEnabled = false

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380L
            interpolator = PathInterpolator(.2f, .78f, .2f, 1f)
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                host.layoutParams = host.layoutParams.apply {
                    height = (startHeight + (targetHeight - startHeight) * progress).toInt()
                }
                previous.alpha = 1f - progress
                previous.translationX = -distance * .45f * progress
                next.alpha = progress
                next.translationX = distance * (1f - progress)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    host.removeView(previous)
                    next.alpha = 1f
                    next.translationX = 0f
                    host.layoutParams = host.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
                    host.isEnabled = true
                }
            })
            start()
        }
    }

    /**
     * 全校课表使用与个人登录不同的筛选表单：标签位于值的上方，字段底部使用
     * 一条轻量分隔线，整体更接近课程查询页面而不是账号输入页面。
     */
    private fun buildPublicLoginPage(): View {
        val scroll = ScheduleScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(PUBLIC_PAGE_BACKGROUND)
        }
        val viewport = verticalLayout().apply {
            gravity = Gravity.CENTER
            setPadding(dp(20), 0, dp(20), dp(24))
        }
        scroll.addView(viewport, FrameLayout.LayoutParams(-1, -2))

        val card = surfaceCard(dp(28f).toFloat()).apply {
            setCardBackgroundColor(PUBLIC_SURFACE)
            setStrokeColor(PUBLIC_CARD_OUTLINE)
            cardElevation = dp(2).toFloat()
        }
        val body = verticalLayout().apply {
            setPadding(dp(24), dp(24), dp(24), dp(22))
        }

        body.addView(text("登录", 28f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(8)))

        // 全校课表与个人登录使用不同的表单构建分支，切换器需要
        // 在两个分支中都显式加入，否则进入全校课表后会丢失顶部胶囊。
        val modeToggle = LoginModeToggle(this, loginMode) { nextMode, _ ->
            if (nextMode != loginMode) {
                loginMode = nextMode
                swapPage(buildLoginPage(), nextMode == LoginMode.PUBLIC, true)
            }
        }
        body.addView(modeToggle, LinearLayout.LayoutParams(-1, dp(60)).apply {
            leftMargin = dp(16)
            rightMargin = dp(16)
            bottomMargin = dp(18)
        })

        semesterInput = MaterialAutoCompleteTextView(this)
        val semesterOptions = semesterOptions().toList()
        val savedTerm = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_TERM, "")
        val selectedTerm = savedTerm?.takeIf { it in semesterOptions } ?: semesterOptions.first()
        semesterInput.setText(selectedTerm, false)
        body.addView(publicFormField("学期", semesterInput, semesterOptions, selectedTerm) {
            showSemesterPicker()
        }, spacedParams(dp(14)))

        buildPublicFilterFields(body, selectedTerm)

        loginStatus = text("", 13f, ERROR, Typeface.NORMAL).apply {
            visibility = View.GONE
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        body.addView(loginStatus, spacedParams(dp(12)))

        val login = MaterialButton(this).apply {
            text = "查询班级课表"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            isAllCaps = false
            cornerRadius = dp(23)
            insetTop = 0
            insetBottom = 0
            minimumHeight = dp(46)
            backgroundTintList = buttonColors()
            setOnClickListener { attemptPublicScheduleLookup() }
        }
        if (!hasPublicScheduleCache(selectedTerm)) {
            login.isEnabled = false
            login.text = "正在准备全校课表…"
        }
        loginButton = login
        body.addView(login, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(6)
        })

        card.addView(body)
        viewport.addView(card, matchWrapParams())
        viewport.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val available = view.width - view.paddingLeft - view.paddingRight
            val width = minOf(available, dp(480))
            val params = card.layoutParams
            if (width > 0 && params.width != width) {
                params.width = width
                card.layoutParams = params
            }
        }
        return scroll
    }

    private fun attemptLogin() {
        studentIdBox.error = null
        passwordBox.error = null
        semesterInput.error = null
        loginStatus?.visibility = View.GONE
        val id = studentId.text?.toString()?.trim().orEmpty()
        val pwd = password.text?.toString().orEmpty()
        val selectedSemester = semesterInput.text?.toString()?.trim().orEmpty()
        if (id.isEmpty()) { studentIdBox.error = "请输入学号"; studentId.requestFocus(); return }
        if (pwd.isEmpty()) { passwordBox.error = "请输入密码"; password.requestFocus(); return }
        if (selectedSemester.isEmpty()) { semesterInput.error = "请选择学期"; return }
        if (id == "114514") {
            if (pwd != "admin") {
                passwordBox.error = "密码不正确"
                password.requestFocus()
                return
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_ACCOUNT, id)
                .putString(KEY_PASSWORD, pwd)
                .putString(KEY_TERM, selectedSemester)
                .putString(KEY_SCORE_TERM, selectedSemester)
                .putString(KEY_STUDENT_NAME, "演示用户")
                .remove(KEY_SCORES)
                .remove(KEY_EXAMS)
                .apply()
            saveCourseCache(sampleCourses())
            saveScoreCache(sampleScoreResult(selectedSemester))
            saveExamCache(selectedSemester, sampleExams())
            showSchedulePage()
            return
        }
        loginButton?.isEnabled = false
        loginButton?.text = "正在查询课程…"
        networkExecutor.execute {
            try {
                val repository = SdauCourseRepository()
                val remoteCourses = repository.queryCourses(id, pwd, selectedSemester)
                // 个人主页中的 infoContentTitle 是教务系统显示“姓名-学号”的来源。
                // 姓名获取失败不影响课程登录，成绩导出会回退为仅显示学号。
                val profile = runCatching { repository.queryStudentProfile(id, pwd) }.getOrNull()
                val courses = recolorCourses(remoteCourses.map { remote ->
                    Course(remote.day, remote.startSlot, remote.slotCount, remote.name, remote.room, remote.teacher, COURSE_COLORS.first(), Color.WHITE, remote.weeks)
                })
                runOnUiThread {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putString(KEY_ACCOUNT, id)
                        .putString(KEY_PASSWORD, pwd)
                        .putString(KEY_TERM, selectedSemester)
                        .putString(KEY_SCORE_TERM, selectedSemester)
                        .putString(KEY_STUDENT_NAME, profile?.name.orEmpty())
                        .remove(KEY_SCORES)
                        .remove(KEY_EXAMS)
                        .apply()
                    saveCourseCache(courses)
                    loginButton?.isEnabled = true
                    loginButton?.text = "进入课程表"
                    showSchedulePage()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    showLoginError(error)
                    loginButton?.isEnabled = true
                    loginButton?.text = "进入课程表"
                }
            }
        }
    }

    private fun publicGradeLabel(value: String): String {
        val normalized = value.trim().removeSuffix("级")
        return if (normalized.isBlank()) "未标注年级" else "${normalized}级"
    }

    private fun publicSelectorBox(
        label: String,
        options: List<String>,
        selected: String,
        onSelected: (String) -> Unit
    ): TextInputLayout = publicSelectorBox(label, options, selected, onSelected, true)

    private fun publicSelectorBox(
        label: String,
        options: List<String>,
        selected: String,
        onSelected: (String) -> Unit,
        enabled: Boolean
    ): TextInputLayout {
        val selectorEnabled = enabled && when (label) {
            "年级" -> publicCollegeSelection.isNotBlank()
            "专业" -> publicGradeSelection.isNotBlank()
            "班级" -> publicMajorSelection.isNotBlank()
            else -> true
        }
        val box = selectorInputBox(label).apply {
            isEnabled = selectorEnabled
            alpha = if (selectorEnabled) 1f else .55f
        }
        val field = MaterialAutoCompleteTextView(this).apply {
            setText(selected.takeIf { it in options }.orEmpty(), false)
            setTextColor(TEXT_PRIMARY)
            textSize = 18f
            inputType = InputType.TYPE_NULL
            isFocusable = false
            minHeight = dp(54)
            setPadding(dp(16), dp(6), dp(16), dp(2))
            background = selectorFieldBackground(selectorEnabled)
            gravity = Gravity.BOTTOM
            isEnabled = selectorEnabled
            setOnClickListener {
                if (!selectorEnabled) return@setOnClickListener
                showPublicOptionPicker(label, options, text?.toString().orEmpty()) { value ->
                    setText(value, false)
                    onSelected(value)
                }
            }
        }
        box.addView(field)
        return box
    }

    private fun showPublicOptionPicker(
        title: String,
        options: List<String>,
        selected: String,
        onSelected: (String) -> Unit
    ) {
        if (publicOptionOverlay != null || options.isEmpty()) return
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(145, 12, 18, 30))
            isClickable = true
            setOnClickListener { hidePublicOptionPicker() }
        }
        val card = surfaceCard(dp(24f).toFloat()).apply {
            setCardBackgroundColor(PAGE_BACKGROUND)
            setOnClickListener { }
        }
        val body = verticalLayout().apply { setPadding(dp(20), dp(18), dp(20), dp(18)) }
        body.addView(text("选择$title", 20f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(12)))
        val optionList = verticalLayout()
        options.forEach { option ->
            val row = MaterialCardView(this).apply {
                radius = dp(13f).toFloat()
                cardElevation = 0f
                strokeWidth = 0
                setCardBackgroundColor(if (option == selected) PRIMARY_CONTAINER else Color.TRANSPARENT)
                setOnClickListener {
                    hidePublicOptionPicker()
                    onSelected(option)
                }
            }
            val rowContent = horizontalLayout().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(option, 15f, TEXT_PRIMARY, Typeface.NORMAL).apply {
                    setPadding(dp(14), dp(11), dp(14), dp(11))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                if (option == selected) {
                    addView(ImageView(this@MainActivity).apply {
                        setImageResource(R.drawable.ic_check)
                        setColorFilter(PRIMARY_DARK)
                        setPadding(dp(8), dp(8), dp(14), dp(8))
                    }, LinearLayout.LayoutParams(dp(40), dp(48)))
                }
            }
            row.addView(rowContent)
            optionList.addView(row, spacedParams(dp(7)))
        }
        if (title == "学院") {
            val scroll = ScrollView(this).apply {
                isVerticalScrollBarEnabled = true
                isScrollbarFadingEnabled = false
                scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(optionList, FrameLayout.LayoutParams(-1, -2))
            }
            body.addView(scroll, LinearLayout.LayoutParams(-1, dp(6 * 52)))
        } else {
            body.addView(optionList)
        }
        card.addView(body)
        val width = minOf(dp(360f), resources.displayMetrics.widthPixels - dp(36f))
        overlay.addView(card, FrameLayout.LayoutParams(width, -2, Gravity.CENTER))
        pageHost.addView(overlay, matchParentParams())
        publicOptionOverlay = overlay
        overlay.alpha = 0f
        card.scaleX = .94f
        card.scaleY = .94f
        overlay.animate().alpha(1f).setDuration(150).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(190).start()
    }

    private fun hidePublicOptionPicker() {
        val overlay = publicOptionOverlay ?: return
        overlay.animate().alpha(0f).setDuration(130).withEndAction {
            pageHost.removeView(overlay)
            publicOptionOverlay = null
        }.start()
    }

    private fun choosePublicOption(current: String, options: List<String>, preferred: List<String>): String {
        return current.takeIf { it in options }
            ?: preferred.firstOrNull { it in options }
            ?: ""
    }

    private fun buildPublicFilterFields(body: LinearLayout, term: String) {
        val records = loadPublicScheduleCache(term)
        val colleges = records.map { it.college }.filter { it.isNotBlank() }.distinct().sorted()
        val college = choosePublicOption(publicCollegeSelection, colleges, listOf("农学院"))
        val gradeRecords = records.filter { college.isBlank() || it.college == college }
        val grades = gradeRecords.map { publicGradeLabel(it.grade) }.distinct().sorted()
        val grade = choosePublicOption(publicGradeSelection, grades, listOf("2026级"))
        val majorRecords = gradeRecords.filter { grade.isBlank() || publicGradeLabel(it.grade) == grade }
        val majors = majorRecords.map { it.major }.filter { it.isNotBlank() }.distinct().sorted()
        val major = choosePublicOption(
            publicMajorSelection,
            majors,
            listOf("农业（拔尖基地班）", "农学（拔尖基地班）")
        )
        val classRecords = majorRecords.filter { major.isBlank() || it.major == major }
        val classes = classRecords.map { it.className }.filter { it.isNotBlank() }.distinct().sorted()
        val className = choosePublicOption(publicClassSelection, classes, listOf("农基2601"))

        publicCollegeSelection = college
        publicGradeSelection = grade
        publicMajorSelection = major
        publicClassSelection = className

        publicCollegeInput = MaterialAutoCompleteTextView(this)
        body.addView(publicFormField("学院", publicCollegeInput, colleges, college) {
            publicCollegeSelection = it
            publicGradeSelection = ""
            publicMajorSelection = ""
            publicClassSelection = ""
            swapPage(buildLoginPage(), false, false)
        }, spacedParams(dp(14)))
        publicGradeInput = MaterialAutoCompleteTextView(this)
        body.addView(publicFormField("年级", publicGradeInput, grades, grade) {
            publicGradeSelection = it
            publicMajorSelection = ""
            publicClassSelection = ""
            swapPage(buildLoginPage(), false, false)
        }, spacedParams(dp(14)))
        publicMajorInput = MaterialAutoCompleteTextView(this)
        body.addView(publicFormField("专业", publicMajorInput, majors, major) {
            publicMajorSelection = it
            publicClassSelection = ""
            swapPage(buildLoginPage(), false, false)
        }, spacedParams(dp(14)))
        publicClassInput = MaterialAutoCompleteTextView(this)
        body.addView(publicFormField("班级", publicClassInput, classes, className) {
            publicClassSelection = it
            swapPage(buildLoginPage(), false, false)
        }, spacedParams(dp(14)))
        if (records.isEmpty()) {
            body.addView(text("全校课表正在准备，准备完成后可筛选查询", 13f, TEXT_SECONDARY, Typeface.NORMAL).apply {
                setLineSpacing(dp(3).toFloat(), 1f)
            }, spacedParams(dp(8)))
        }
    }

    private fun publicFormField(
        label: String,
        field: MaterialAutoCompleteTextView,
        options: List<String>,
        selected: String = "",
        onSelected: (String) -> Unit
    ): View {
        val enabled = options.isNotEmpty()
        val container = verticalLayout()
        container.addView(text(label, 14f, TEXT_SECONDARY, Typeface.NORMAL).apply {
            includeFontPadding = true
        }, matchWrapParams())
        field.apply {
            setText(selected.takeIf { it in options }.orEmpty(), false)
            setTextColor(TEXT_PRIMARY)
            textSize = 18f
            setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
            inputType = InputType.TYPE_NULL
            isFocusable = false
            isEnabled = enabled
            alpha = if (enabled) 1f else .55f
            minHeight = dp(34)
            setPadding(0, 0, 0, dp(2))
            gravity = Gravity.CENTER_VERTICAL
            background = ColorDrawable(Color.TRANSPARENT)
            setOnClickListener {
                if (!enabled) return@setOnClickListener
                showPublicOptionPicker(label, options, text?.toString().orEmpty()) { value ->
                    setText(value, false)
                    onSelected(value)
                }
            }
        }
        container.addView(field, matchWrapParams())
        container.addView(View(this).apply {
            setBackgroundColor(PUBLIC_FIELD_DIVIDER)
            alpha = if (enabled) 1f else .55f
        }, LinearLayout.LayoutParams(-1, dp(1)))
        return container
    }

    private fun attemptPublicScheduleLookup() {
        val term = semesterInput.text?.toString()?.trim().orEmpty()
        val records = loadPublicScheduleCache(term)
        if (records.isEmpty()) {
            loginStatus?.text = "暂无本地全校课表缓存，请先使用个人账号登录"
            loginStatus?.visibility = View.VISIBLE
            return
        }
        if (publicCollegeSelection.isBlank() || publicGradeSelection.isBlank() ||
            publicMajorSelection.isBlank() || publicClassSelection.isBlank()
        ) {
            loginStatus?.text = "请选择学院、年级、专业和班级"
            loginStatus?.visibility = View.VISIBLE
            return
        }
        val selected = records.filter {
            it.college == publicCollegeSelection &&
                publicGradeLabel(it.grade) == publicGradeSelection &&
                it.major == publicMajorSelection &&
                it.className == publicClassSelection
        }
        if (selected.isEmpty()) {
            loginStatus?.text = "未找到该班级的课程信息"
            loginStatus?.visibility = View.VISIBLE
            return
        }
        val colors = selected.map { it.name }.distinct().withIndex().associate { it.value to COURSE_COLORS[it.index % COURSE_COLORS.size] }
        publicScheduleCourses = selected.map {
            Course(it.day, it.startSlot, it.slotCount, it.name, it.room, it.teacher,
                colors[it.name] ?: COURSE_COLORS.first(), Color.WHITE, it.weeks)
        }
        publicScheduleTerm = term
        publicScheduleLabel = "$publicCollegeSelection · $publicGradeSelection · $publicMajorSelection · $publicClassSelection"
        publicScheduleClassName = publicClassSelection
        viewingPublicSchedule = true
        currentWeek = weekForTerm(term)
        showSchedulePage()
    }

    private fun showLoginError(error: Exception) {
        val detail = error.message?.replace(Regex("\\s+"), " ")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(180)
            ?: "教务系统暂时不可用，请稍后重试"
        loginStatus?.apply {
            text = "查询失败：$detail"
            visibility = View.VISIBLE
        }
    }

    private fun buildSchedulePage(): View {
        val page = FrameLayout(this).apply { background = SilkyGradientDrawable() }
        val navigationHeight = dp(54)
        val navigationBottomMargin = dp(16)
        mainSectionHost = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(buildScheduleSection(), FrameLayout.LayoutParams(-1, -1))
        }
        page.addView(mainSectionHost, FrameLayout.LayoutParams(-1, -1).apply {
            bottomMargin = navigationHeight + navigationBottomMargin + dp(4)
        })
        bottomNavigation = LiquidGlassNavigationView(this).apply {
            onItemSelected = { index, _ -> showMainSection(index) }
        }
        page.addView(bottomNavigation, FrameLayout.LayoutParams(dp(216), navigationHeight, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = navigationBottomMargin
        })
        return page
    }

    private fun buildScheduleSection(): View {
        val section = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        val content = verticalLayout().apply { setBackgroundColor(Color.TRANSPARENT) }
        content.addView(buildScheduleHeader(), matchWrapParams())
        scheduleGrid = ScheduleGridView(this, activeScheduleCourses())
        scheduleGrid?.setScheduleMode(scheduleMode)
        scheduleGrid?.setWeekIndex(currentWeek)
        content.addView(scheduleGrid, LinearLayout.LayoutParams(-1, 0, 1f))
        section.addView(content, FrameLayout.LayoutParams(-1, -1))
        section.addView(text(appDisplayVersion, 10f, Color.rgb(145, 150, 162), Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(10)
            bottomMargin = dp(6)
        })
        return section
    }

    private fun showMainSection(index: Int) {
        if (index == currentMainSection) {
            if (index == 0) jumpToCurrentWeek()
            if (index == 1) refreshExams()
            if (index == 2) refreshScores()
            return
        }
        val host = mainSectionHost ?: return
        val previousIndex = currentMainSection
        currentMainSection = index
        if (index == 3 && emptyRoomResult == null && !emptyRoomsLoading) {
            syncEmptyRoomDefaultsToNow()
        }
        val next = when (index) {
            1 -> buildExamSection()
            2 -> buildGradesSection()
            3 -> buildEmptyRoomSection()
            else -> buildScheduleSection()
        }
        val distance = dp(42).toFloat() * if (index > previousIndex) 1f else -1f
        replaceMainSection(host, next, index, distance, 230L)
    }

    /**
     * 始终只保留“当前页 + 正在进入页”两层，并用代次阻止旧动画清理新页面。
     * 这同时覆盖底栏快速切换和教务数据异步刷新，避免多个半透明页面偶发叠加。
     */
    private fun replaceMainSection(
        host: FrameLayout,
        next: View,
        sectionIndex: Int,
        enterTranslationX: Float = 0f,
        enterDuration: Long = 180L
    ) {
        if (host !== mainSectionHost || currentMainSection != sectionIndex) return
        val generation = ++mainSectionTransitionGeneration

        // 最上层子 View 才是用户当前看到的页面；更早的残留层立即移除。
        val previous = host.getChildAt(host.childCount - 1)
        for (childIndex in host.childCount - 1 downTo 0) {
            val child = host.getChildAt(childIndex)
            child.animate().setListener(null).withEndAction(null).cancel()
            if (child !== previous) host.removeViewAt(childIndex)
        }

        next.animate().setListener(null).withEndAction(null).cancel()
        next.alpha = 0f
        next.translationX = enterTranslationX
        host.addView(next, FrameLayout.LayoutParams(-1, -1))

        next.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(enterDuration)
            .withEndAction {
                if (
                    generation == mainSectionTransitionGeneration &&
                    host === mainSectionHost &&
                    currentMainSection == sectionIndex &&
                    next.parent === host
                ) {
                    // 动画完成后强制恢复单层，防止刷新回调与导航回调交错留下旧页。
                    for (childIndex in host.childCount - 1 downTo 0) {
                        val child = host.getChildAt(childIndex)
                        if (child !== next) {
                            child.animate().setListener(null).withEndAction(null).cancel()
                            host.removeViewAt(childIndex)
                        }
                    }
                    next.alpha = 1f
                    next.translationX = 0f
                }
            }
            .start()

        previous?.animate()
            ?.alpha(0f)
            ?.translationX(-enterTranslationX * .55f)
            ?.setDuration(minOf(160L, enterDuration))
            ?.withEndAction {
                if (previous.parent === host) host.removeView(previous)
            }
            ?.start()
    }

    private fun buildExamSection(refresh: Boolean = true): View {
        if (viewingPublicSchedule) {
            return buildExamStateSection(
                activeScheduleTerm(), hasLoaded = true, error = null,
                emptyDescription = "此功能暂不可用\n请切换回个人账号重新查询"
            )
        }
        val term = activeScheduleTerm()
        val cached = loadExamCache()?.takeIf { it.term == term }
        val section = if (!cached?.records.isNullOrEmpty()) {
            buildExamResultSection(term, cached!!.records)
        } else {
            buildExamStateSection(term, hasLoaded = cached != null, error = examLoadError)
        }
        if (refresh && !examsLoading) section.post { refreshExams() }
        return section
    }

    private fun refreshExams() {
        if (viewingPublicSchedule) return
        if (examsLoading) return
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
        val password = preferences.getString(KEY_PASSWORD, "").orEmpty()
        val term = selectedTerm()
        val cached = loadExamCache()?.takeIf { it.term == term }
        if (account.isBlank() || password.isBlank()) {
            examLoadError = if (cached == null) "登录信息不完整，请重新登录后再查询考试安排。" else null
            if (cached == null) refreshVisibleExams()
            return
        }
        if (account == "114514") {
            examLoadError = null
            val records = sampleExams()
            if (cached?.records != records) {
                saveExamCache(term, records)
                refreshVisibleExams()
            }
            return
        }
        examsLoading = true
        examLoadError = null
        if (cached == null) refreshVisibleExams()
        networkExecutor.execute {
            try {
                val records = SdauCourseRepository().queryExams(account, password, term)
                val changed = cached?.records != records
                saveExamCache(term, records)
                runOnUiThread {
                    examsLoading = false
                    examLoadError = null
                    if (changed) refreshVisibleExams()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    examsLoading = false
                    examLoadError = if (cached == null) {
                        error.message?.replace(Regex("\\s+"), " ")?.take(160)
                            ?: "教务系统暂时无法访问，请稍后重试。"
                    } else null
                    if (cached == null) refreshVisibleExams()
                }
            }
        }
    }

    private fun refreshVisibleExams() {
        if (currentMainSection != 1) return
        val host = mainSectionHost ?: return
        replaceMainSection(host, buildExamSection(refresh = false), 1, 0f, 170L)
    }

    private fun buildExamStateSection(
        term: String,
        hasLoaded: Boolean,
        error: String?,
        emptyTitle: String = "暂无考试安排",
        emptyDescription: String = "本学期暂未发布考试信息\n后续安排会在查询后显示在这里"
    ): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.TRANSPARENT)
        }
        val body = verticalLayout().apply { setPadding(dp(20), dp(18), dp(20), dp(28)) }
        body.addView(text("考试安排", 28f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(7)))
        body.addView(text("$term 学期", 13f, TEXT_SECONDARY, Typeface.NORMAL), matchWrapParams())
        val stateView = when {
            examsLoading || (!hasLoaded && error.isNullOrBlank()) -> verticalLayout().apply {
                gravity = Gravity.CENTER
                addView(ProgressBar(this@MainActivity).apply {
                    indeterminateTintList = ColorStateList.valueOf(PRIMARY)
                    contentDescription = "正在加载考试安排"
                }, LinearLayout.LayoutParams(dp(34), dp(34)))
            }
            !error.isNullOrBlank() -> verticalLayout().apply {
                gravity = Gravity.CENTER
                setPadding(dp(22), dp(20), dp(22), dp(20))
                addView(text("!", 20f, ERROR, Typeface.BOLD).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(48), dp(42)))
                addView(text(error, 14f, TEXT_SECONDARY, Typeface.NORMAL).apply {
                    gravity = Gravity.CENTER
                    setLineSpacing(dp(3).toFloat(), 1f)
                }, matchWrapParams())
                isClickable = true
                contentDescription = "考试安排加载失败，点击重试"
                setOnClickListener { refreshExams() }
            }
            else -> buildAcademicEmptyState(
                EmptyAcademicState.EXAMS,
                emptyTitle,
                emptyDescription
            )
        }
        body.addView(stateView, LinearLayout.LayoutParams(-1, 0, 1f))
        scroll.addView(body, FrameLayout.LayoutParams(-1, -1))
        return scroll
    }

    private fun buildAcademicEmptyState(
        type: EmptyAcademicState,
        title: String,
        description: String
    ): View = verticalLayout().apply {
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(20), dp(14), dp(24))
        addView(AcademicEmptyIllustration(this@MainActivity, type), LinearLayout.LayoutParams(dp(158), dp(132)).apply {
            bottomMargin = dp(14)
        })
        addView(text(title, 18f, TEXT_PRIMARY, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }, spacedParams(dp(10)))
        addView(text(description, 13f, TEXT_SECONDARY, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
        }, matchWrapParams())
    }

    private fun buildExamResultSection(term: String, records: List<RemoteExam>): View {
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.TRANSPARENT)
        }
        val body = verticalLayout().apply { setPadding(dp(20), dp(18), dp(20), dp(28)) }
        body.addView(text("考试安排", 28f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(7)))
        body.addView(text("$term 学期 · ${records.size} 门考试", 13f, TEXT_SECONDARY, Typeface.NORMAL), spacedParams(dp(18)))
        records.sortedWith(compareBy<RemoteExam>(
            { it.examWeek.toIntOrNull() ?: Int.MAX_VALUE },
            { it.examWeekday.toIntOrNull() ?: Int.MAX_VALUE },
            { Regex("\\d+").find(it.examSessions)?.value?.toIntOrNull() ?: Int.MAX_VALUE }
        )).forEach { exam ->
            val card = surfaceCard(dp(24f).toFloat()).apply {
                cardElevation = 0f
                strokeWidth = 0
                // 半透明暖白玻璃：保留清晰的卡片层级，同时让部分页面渐变透出。
                setCardBackgroundColor(Color.argb(154, 250, 252, 255))
            }
            val content = verticalLayout().apply { setPadding(dp(18), dp(17), dp(18), dp(17)) }
            content.addView(text(exam.courseName, 18f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(15)))
            val firstRow = horizontalLayout()
            firstRow.addView(examDetail("考试周数", "第${exam.examWeek}周"), LinearLayout.LayoutParams(0, -2, 1f))
            firstRow.addView(examDetail("考试星期", exam.examWeekday), LinearLayout.LayoutParams(0, -2, 1f))
            content.addView(firstRow, spacedParams(dp(13)))
            val secondRow = horizontalLayout()
            secondRow.addView(examDetail("考试节次", "${exam.examSessions}节"), LinearLayout.LayoutParams(0, -2, 1f))
            secondRow.addView(examDetail("考试教室", exam.classroom), LinearLayout.LayoutParams(0, -2, 1f))
            content.addView(secondRow, matchWrapParams())
            card.addView(content)
            body.addView(card, spacedParams(dp(12)))
        }
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        return scroll
    }

    private fun examDetail(label: String, value: String): View = verticalLayout().apply {
        addView(text(label, 12f, TEXT_SECONDARY, Typeface.NORMAL), spacedParams(dp(5)))
        addView(text(value.ifBlank { "-" }, 15f, TEXT_PRIMARY, Typeface.BOLD), matchWrapParams())
    }

    private fun buildGradesSection(refresh: Boolean = true): View {
        if (viewingPublicSchedule) {
            return buildGradeStateSection(
                activeScheduleTerm(), hasLoadedResult = true, error = null,
                emptyTitle = "暂无成绩信息",
                emptyDescription = "此功能暂不可用\n请切换回个人账号重新查询"
            )
        }
        val selectedTerm = selectedScoreTerm()
        val cached = loadScoreCache()?.takeIf { it.term == selectedTerm }
        val result = if (cached != null && cached.records.isNotEmpty()) {
            buildScoreResultSection(cached)
        } else {
            buildGradeStateSection(selectedTerm, cached != null, scoreLoadError)
        }
        if (refresh && !scoresLoading) result.post { refreshScores() }
        return result
    }

    private fun refreshScores() {
        if (viewingPublicSchedule) return
        if (scoresLoading) return
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
        val password = preferences.getString(KEY_PASSWORD, "").orEmpty()
        val term = selectedScoreTerm()
        val cached = loadScoreCache()?.takeIf { it.term == term }
        if (account.isBlank() || password.isBlank()) {
            scoreLoadError = if (cached == null) "登录信息不完整，请重新登录后再查询成绩。" else null
            if (cached == null) refreshVisibleGrades()
            return
        }
        if (account == "114514") {
            scoreLoadError = null
            if (preferences.getString(KEY_STUDENT_NAME, "").orEmpty().isBlank()) {
                preferences.edit().putString(KEY_STUDENT_NAME, "演示用户").apply()
            }
            val result = sampleScoreResult(term)
            if (cached != result) {
                saveScoreCache(result)
                refreshVisibleGrades()
            }
            return
        }
        scoresLoading = true
        scoreLoadError = null
        if (cached == null) refreshVisibleGrades()
        networkExecutor.execute {
            try {
                val repository = SdauCourseRepository()
                val profile = if (preferences.getString(KEY_STUDENT_NAME, "").orEmpty().isBlank()) {
                    runCatching { repository.queryStudentProfile(account, password) }.getOrNull()
                } else {
                    null
                }
                profile?.let { saveStudentName(it.name) }
                val result = repository.queryScores(account, password, term, allScoreTerms(account))
                val changed = cached != result
                saveScoreCache(result)
                runOnUiThread {
                    scoresLoading = false
                    scoreLoadError = null
                    if (changed) refreshVisibleGrades()
                    if (selectedScoreTerm() != term) refreshScores()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    scoresLoading = false
                    scoreLoadError = if (cached == null) {
                        error.message?.replace(Regex("\\s+"), " ")?.take(160)
                            ?: "教务系统暂时无法访问，请稍后重试。"
                    } else null
                    if (cached == null) refreshVisibleGrades()
                    if (selectedScoreTerm() != term) refreshScores()
                }
            }
        }
    }

    private fun refreshVisibleGrades() {
        if (currentMainSection != 2) return
        val host = mainSectionHost ?: return
        replaceMainSection(host, buildGradesSection(refresh = false), 2, 0f, 170L)
    }

    private fun buildEmptyRoomSection(): View {
        val visibleResult = emptyRoomResult?.takeIf {
            it.campus == emptyRoomCampus && it.week == emptyRoomWeek &&
                it.weekday == emptyRoomWeekday && it.sectionCode == emptyRoomSectionCode
        }
        val visibleGroups = visibleResult?.let(::groupEmptyRooms).orEmpty()
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.TRANSPARENT)
        }
        val body = verticalLayout().apply { setPadding(dp(20), dp(18), dp(20), dp(28)) }
        body.addView(text("教室使用情况", 28f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(18)))
        body.addView(buildEmptyRoomQueryPanel(), spacedParams(dp(26)))

        val resultHeader = horizontalLayout().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        resultHeader.addView(text("查询结果", 20f, TEXT_PRIMARY, Typeface.BOLD), LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(resultHeader, spacedParams(dp(7)))
        body.addView(text(
            "${emptyRoomCampus} · 第${emptyRoomWeek}周 · ${emptyRoomWeekdayLabel(emptyRoomWeekday)} · ${emptyRoomSectionLabel(emptyRoomSectionCode)}",
            12f,
            TEXT_SECONDARY,
            Typeface.NORMAL
        ).apply { setPadding(dp(16), 0, dp(16), 0) }, spacedParams(dp(15)))

        val state = when {
            emptyRoomsLoading -> emptyRoomResultSurface().apply {
                minimumHeight = dp(260)
                val loading = verticalLayout().apply {
                    gravity = Gravity.CENTER
                    addView(ProgressBar(this@MainActivity).apply {
                        isIndeterminate = true
                        indeterminateTintList = ColorStateList.valueOf(PRIMARY)
                        contentDescription = "正在查询空教室"
                    }, LinearLayout.LayoutParams(dp(36), dp(36)))
                    addView(text("正在整理空闲教室", 13f, TEXT_SECONDARY, Typeface.NORMAL).apply {
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
                }
                addView(loading, FrameLayout.LayoutParams(-1, dp(260)))
            }
            !emptyRoomLoadError.isNullOrBlank() -> emptyRoomResultSurface().apply {
                minimumHeight = dp(260)
                val errorView = verticalLayout().apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(22), dp(32), dp(22), dp(32))
                    addView(text("!", 21f, ERROR, Typeface.BOLD).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(50), dp(44)).apply {
                        bottomMargin = dp(8)
                    })
                    addView(text(emptyRoomLoadError.orEmpty(), 14f, TEXT_SECONDARY, Typeface.NORMAL).apply {
                        gravity = Gravity.CENTER
                        setLineSpacing(dp(4).toFloat(), 1f)
                    }, matchWrapParams())
                }
                addView(errorView, FrameLayout.LayoutParams(-1, dp(260)))
                isClickable = true
                contentDescription = "空教室查询失败，点击重试"
                setOnClickListener { refreshEmptyRooms() }
            }
            visibleResult == null -> buildEmptyRoomIdleState()
            visibleGroups.isEmpty() -> emptyRoomResultSurface().apply {
                addView(buildAcademicEmptyState(
                    EmptyAcademicState.ROOMS,
                    "暂无空闲教室",
                    "当前校区与时段暂未找到可用教室\n可以更换星期或节次后再查询"
                ), FrameLayout.LayoutParams(-1, dp(310)))
            }
            else -> buildEmptyRoomResults(requireNotNull(visibleResult), visibleGroups)
        }
        body.addView(state, matchWrapParams())
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        return scroll
    }

    private fun buildEmptyRoomQueryPanel(): View = MaterialCardView(this).apply {
        radius = dp(25f).toFloat()
        cardElevation = 0f
        strokeWidth = 0
        setCardBackgroundColor(Color.TRANSPARENT)
        val panel = verticalLayout().apply {
            setPadding(dp(16), dp(13), dp(16), dp(16))
            background = ColorDrawable(Color.TRANSPARENT)
        }
        val titleRow = horizontalLayout().apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(text("查询条件", 20f, TEXT_PRIMARY, Typeface.BOLD), LinearLayout.LayoutParams(0, -2, 1f))
        val queryButton = ImageButton(this@MainActivity).apply {
            setImageResource(R.drawable.ic_search_room)
            imageTintList = ColorStateList.valueOf(PRIMARY_DARK)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(7), dp(7), dp(7), dp(7))
            contentDescription = if (emptyRoomsLoading) "正在查询空教室" else "查询空教室"
            isEnabled = !emptyRoomsLoading
            alpha = if (emptyRoomsLoading) .58f else 1f
            background = ColorDrawable(Color.TRANSPARENT)
            visibility = if (emptyRoomQueryExpanded) View.VISIBLE else View.GONE
            setOnClickListener { refreshEmptyRooms() }
        }
        titleRow.addView(queryButton, LinearLayout.LayoutParams(dp(34), dp(34)))
        val toggle = emptyRoomCollapseButton(emptyRoomQueryExpanded, "查询条件")
        titleRow.addView(toggle, LinearLayout.LayoutParams(dp(34), dp(34)).apply { leftMargin = dp(2) })
        titleRow.isClickable = true
        titleRow.isFocusable = true
        titleRow.contentDescription = if (emptyRoomQueryExpanded) "折叠查询条件" else "展开查询条件"
        panel.addView(titleRow, matchWrapParams())

        val queryControls = verticalLayout()

        val firstFilters = horizontalLayout()
        firstFilters.addView(emptyRoomFilterCard("校区", emptyRoomCampus) {
            showEmptyRoomFilterPicker(
                "选择校区",
                listOf("岱宗校区", "泮河校区", "西北片区").map { it to it },
                emptyRoomCampus
            ) {
                emptyRoomCampus = it
                onEmptyRoomFilterChanged()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        firstFilters.addView(emptyRoomFilterCard("周次", "第${emptyRoomWeek}周") {
            showEmptyRoomFilterPicker(
                "选择周次",
                (1..20).map { "第${it}周" to it.toString() },
                emptyRoomWeek.toString()
            ) {
                emptyRoomWeek = it.toIntOrNull()?.coerceIn(1, 20) ?: emptyRoomWeek
                onEmptyRoomFilterChanged()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        queryControls.addView(firstFilters, spacedParams(dp(10)))

        val secondFilters = horizontalLayout()
        secondFilters.addView(emptyRoomFilterCard("星期", emptyRoomWeekdayLabel(emptyRoomWeekday)) {
            showEmptyRoomFilterPicker(
                "选择星期",
                (1..7).map { emptyRoomWeekdayLabel(it) to it.toString() },
                emptyRoomWeekday.toString()
            ) {
                emptyRoomWeekday = it.toIntOrNull()?.coerceIn(1, 7) ?: emptyRoomWeekday
                onEmptyRoomFilterChanged()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        secondFilters.addView(emptyRoomFilterCard("节次", emptyRoomSectionLabel(emptyRoomSectionCode)) {
            val options = listOf(
                "第一大节" to "0102", "第二大节" to "0304", "中午" to "中午",
                "第三大节" to "0506", "第四大节" to "0708", "第五大节" to "0910",
                "晚间" to "晚间"
            )
            showEmptyRoomFilterPicker("选择节次", options, emptyRoomSectionCode) {
                emptyRoomSectionCode = it
                onEmptyRoomFilterChanged()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        queryControls.addView(secondFilters, matchWrapParams())
        panel.addView(queryControls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) })
        if (!emptyRoomQueryExpanded) queryControls.visibility = View.GONE
        val toggleQuery: () -> Unit = toggle@ {
            if (!toggle.isClickable) return@toggle
            val expanding = !emptyRoomQueryExpanded
            emptyRoomQueryExpanded = expanding
            titleRow.contentDescription = if (expanding) "折叠查询条件" else "展开查询条件"
            queryButton.animate().cancel()
            if (expanding) {
                queryButton.visibility = View.VISIBLE
                queryButton.alpha = 0f
                queryButton.animate()
                    .alpha(if (emptyRoomsLoading) .58f else 1f)
                    .setDuration(180L)
                    .setStartDelay(55L)
                    .start()
            } else {
                queryButton.animate()
                    .alpha(0f)
                    .setDuration(110L)
                    .withEndAction {
                        if (!emptyRoomQueryExpanded) queryButton.visibility = View.GONE
                        queryButton.alpha = if (emptyRoomsLoading) .58f else 1f
                    }
                    .start()
            }
            animateEmptyRoomCollapsible(panel, queryControls, toggle, expanding, "查询条件")
        }
        titleRow.setOnClickListener { toggleQuery() }
        toggle.setOnClickListener { toggleQuery() }
        addView(panel)
    }

    private fun emptyRoomCollapseButton(expanded: Boolean, targetName: String): ImageButton = ImageButton(this).apply {
        setImageResource(R.drawable.ic_expand_chevron)
        imageTintList = ColorStateList.valueOf(PRIMARY_DARK)
        scaleType = ImageView.ScaleType.CENTER
        setPadding(dp(7), dp(7), dp(7), dp(7))
        background = ColorDrawable(Color.TRANSPARENT)
        rotation = if (expanded) 180f else 0f
        contentDescription = if (expanded) "折叠$targetName" else "展开$targetName"
        isClickable = true
        isFocusable = true
    }

    private fun animateEmptyRoomCollapsible(
        panel: LinearLayout,
        controls: View,
        toggle: ImageButton,
        expanding: Boolean,
        targetName: String
    ) {
        toggle.isClickable = false
        controls.animate().cancel()
        toggle.animate().cancel()
        toggle.animate()
            .rotation(if (expanding) 180f else 0f)
            .setDuration(230L)
            .setInterpolator(PathInterpolator(.2f, .78f, .2f, 1f))
            .start()

        val layoutParams = controls.layoutParams
        val expandedLayoutHeight = layoutParams.height
        val startHeight: Int
        val endHeight: Int
        if (expanding) {
            controls.visibility = View.VISIBLE
            controls.alpha = 0f
            controls.translationY = -dp(5f).toFloat()
            startHeight = 0
            endHeight = if (expandedLayoutHeight > 0) {
                expandedLayoutHeight
            } else {
                val availableWidth = (panel.width - panel.paddingLeft - panel.paddingRight).coerceAtLeast(1)
                controls.measure(
                    View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                controls.measuredHeight
            }
            layoutParams.height = 0
        } else {
            startHeight = controls.height.coerceAtLeast(controls.measuredHeight)
            endHeight = 0
        }
        controls.layoutParams = layoutParams

        ValueAnimator.ofInt(startHeight, endHeight).apply {
            duration = 260L
            interpolator = PathInterpolator(.2f, .78f, .2f, 1f)
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                layoutParams.height = animator.animatedValue as Int
                controls.layoutParams = layoutParams
                controls.alpha = if (expanding) fraction else 1f - fraction
                controls.translationY = if (expanding) -dp(5f) * (1f - fraction) else -dp(5f) * fraction
                panel.requestLayout()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    layoutParams.height = expandedLayoutHeight
                    controls.layoutParams = layoutParams
                    controls.alpha = 1f
                    controls.translationY = 0f
                    controls.visibility = if (expanding) View.VISIBLE else View.GONE
                    toggle.contentDescription = if (expanding) "折叠$targetName" else "展开$targetName"
                    toggle.isClickable = true
                }
            })
            start()
        }
    }

    private fun emptyRoomFilterCard(label: String, value: String, onClick: () -> Unit): View = MaterialCardView(this).apply {
        radius = dp(15f).toFloat()
        cardElevation = 0f
        strokeWidth = 0
        setCardBackgroundColor(Color.argb(102, 255, 255, 255))
        isClickable = true
        contentDescription = "$label，当前$value"
        setOnClickListener { onClick() }
        val content = verticalLayout().apply { setPadding(dp(13), dp(10), dp(11), dp(10)) }
        content.addView(text(label, 11f, TEXT_SECONDARY, Typeface.NORMAL), spacedParams(dp(5)))
        val valueRow = horizontalLayout().apply { gravity = Gravity.CENTER_VERTICAL }
        valueRow.addView(text(value, 13.5f, TEXT_PRIMARY, Typeface.BOLD).apply {
            maxLines = 1
        }, LinearLayout.LayoutParams(0, -2, 1f))
        valueRow.addView(text("⌄", 14f, TEXT_SECONDARY, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(18), -2))
        content.addView(valueRow, matchWrapParams())
        addView(content)
    }

    private fun emptyRoomResultSurface(): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(23f).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        strokeColor = Color.argb(92, 255, 255, 255)
        setCardBackgroundColor(Color.argb(104, 216, 225, 242))
    }

    private fun buildEmptyRoomIdleState(): View = emptyRoomResultSurface().apply {
        addView(buildAcademicEmptyState(
            EmptyAcademicState.ROOM_QUERY,
            "等待查询",
            "选择条件后点击查询"
        ), FrameLayout.LayoutParams(-1, dp(300)))
    }

    private fun buildEmptyRoomResults(
        result: RemoteEmptyRoomResult,
        groups: List<EmptyRoomGroup>
    ): View = verticalLayout().apply {
        contentDescription = "${result.campus}空闲教室列表"
        groups.forEachIndexed { groupIndex, group ->
            val groupStateKey = "${result.campus}:${group.title}"
            val groupExpanded = groupStateKey !in collapsedEmptyRoomGroups
            val groupCard = MaterialCardView(this@MainActivity).apply {
                radius = dp(21f).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                strokeColor = Color.argb(118, 255, 255, 255)
                setCardBackgroundColor(blendColors(Color.rgb(231, 236, 247), group.accent, .055f))
            }
            val groupBody = verticalLayout().apply { setPadding(dp(15), dp(14), dp(15), dp(15)) }
            val header = horizontalLayout().apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(View(this@MainActivity).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(3f).toFloat()
                    setColor(group.accent)
                }
            }, LinearLayout.LayoutParams(dp(5), dp(23)).apply { rightMargin = dp(10) })
            header.addView(text(group.title, 16f, TEXT_PRIMARY, Typeface.BOLD), LinearLayout.LayoutParams(0, -2, 1f))
            val groupToggle = emptyRoomCollapseButton(groupExpanded, "${group.title}教室")
            header.addView(groupToggle, LinearLayout.LayoutParams(dp(32), dp(32)))
            header.isClickable = true
            header.isFocusable = true
            header.contentDescription = if (groupExpanded) "折叠${group.title}教室" else "展开${group.title}教室"
            groupBody.addView(header, matchWrapParams())

            val roomRows = group.rooms.chunked(2)
            val roomRowHeight = dp(44)
            val roomRowGap = dp(4)
            val roomContent = verticalLayout()
            roomRows.forEachIndexed { rowIndex, pair ->
                val row = horizontalLayout()
                pair.forEachIndexed { index, room ->
                    val roomLabel = text(room, 14f, TEXT_PRIMARY, Typeface.BOLD).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(10), dp(6), dp(8), dp(6))
                        maxLines = 2
                    }
                    row.addView(roomLabel, LinearLayout.LayoutParams(0, -1, 1f).apply {
                        if (index == 0) rightMargin = dp(4) else leftMargin = dp(4)
                    })
                }
                if (pair.size == 1) {
                    row.addView(Space(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = dp(4) })
                }
                roomContent.addView(row, LinearLayout.LayoutParams(-1, roomRowHeight).apply {
                    if (rowIndex < roomRows.lastIndex) bottomMargin = roomRowGap
                })
            }
            val maxVisibleRows = 4
            val roomViewport: View
            val roomViewportHeight: Int
            if (roomRows.size > maxVisibleRows) {
                roomViewport = EmptyRoomPriorityScrollView(this@MainActivity).apply {
                    addView(roomContent, FrameLayout.LayoutParams(-1, -2))
                }
                roomViewportHeight = roomRowHeight * maxVisibleRows + roomRowGap * (maxVisibleRows - 1)
            } else {
                roomViewport = roomContent
                roomViewportHeight = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            groupBody.addView(roomViewport, LinearLayout.LayoutParams(-1, roomViewportHeight).apply {
                topMargin = dp(12)
            })
            if (!groupExpanded) roomViewport.visibility = View.GONE
            val toggleGroup: () -> Unit = toggle@ {
                if (!groupToggle.isClickable) return@toggle
                val expanding = groupStateKey in collapsedEmptyRoomGroups
                if (expanding) collapsedEmptyRoomGroups.remove(groupStateKey)
                else collapsedEmptyRoomGroups.add(groupStateKey)
                header.contentDescription = if (expanding) "折叠${group.title}教室" else "展开${group.title}教室"
                animateEmptyRoomCollapsible(
                    groupBody,
                    roomViewport,
                    groupToggle,
                    expanding,
                    "${group.title}教室"
                )
            }
            header.setOnClickListener { toggleGroup() }
            groupToggle.setOnClickListener { toggleGroup() }
            groupCard.addView(groupBody)
            addView(groupCard, if (groupIndex == groups.lastIndex) matchWrapParams() else spacedParams(dp(11)))
        }
    }

    private fun groupEmptyRooms(result: RemoteEmptyRoomResult): List<EmptyRoomGroup> {
        val rooms = result.rooms.asSequence()
            .map { it.trim().removePrefix("@").trim() }
            .filter { it.isNotBlank() && shouldDisplayEmptyRoom(result.campus, it) }
            .distinctBy { emptyRoomMatchKey(it) }
            .sortedBy(::emptyRoomNaturalSortKey)
            .toList()

        val buckets = when (result.campus) {
            "岱宗校区" -> linkedMapOf(
                ("5N" to Color.rgb(103, 151, 214)) to mutableListOf<String>(),
                ("5S" to Color.rgb(92, 181, 164)) to mutableListOf(),
                ("文理大楼" to Color.rgb(139, 132, 199)) to mutableListOf(),
                ("12号楼" to Color.rgb(220, 156, 96)) to mutableListOf(),
                ("其他教室" to Color.rgb(116, 137, 174)) to mutableListOf()
            )
            "泮河校区" -> linkedMapOf(
                ("中央片区" to Color.rgb(102, 146, 211)) to mutableListOf<String>(),
                ("东南片区" to Color.rgb(219, 132, 116)) to mutableListOf(),
                ("其他教室" to Color.rgb(91, 176, 166)) to mutableListOf()
            )
            else -> linkedMapOf(
                ("22号楼" to Color.rgb(137, 129, 198)) to mutableListOf<String>(),
                ("其他教室" to Color.rgb(103, 151, 190)) to mutableListOf()
            )
        }

        rooms.forEach { room ->
            val key = emptyRoomMatchKey(room)
            val groupKey = when (result.campus) {
                "岱宗校区" -> when {
                    key.startsWith("5N") -> "5N"
                    key.startsWith("5S") -> "5S"
                    key.contains("文理大楼") -> "文理大楼"
                    key.contains("12号楼") -> "12号楼"
                    else -> "其他教室"
                }
                "泮河校区" -> when {
                    key.startsWith("19#") -> "东南片区"
                    key.firstOrNull() in setOf('N', 'W', 'E', 'S') -> "中央片区"
                    else -> "其他教室"
                }
                else -> if (key.startsWith("22#")) "22号楼" else "其他教室"
            }
            buckets.entries.firstOrNull { it.key.first == groupKey }?.value?.add(room)
        }

        return buckets.mapNotNull { (definition, groupedRooms) ->
            groupedRooms.takeIf { it.isNotEmpty() }?.let {
                EmptyRoomGroup(definition.first, definition.second, it)
            }
        }
    }

    private fun shouldDisplayEmptyRoom(campus: String, room: String): Boolean {
        val key = emptyRoomMatchKey(room)
        if (key.contains("线上教学")) return false
        if (campus == "泮河校区") {
            if (key.contains("南校区体育羽毛球馆")) return false
            if (key.contains("南校实践环节地点") && key.contains("化学实践S")) return false
        }
        return true
    }

    private fun emptyRoomMatchKey(room: String): String = room
        .replace(Regex("\\s+"), "")
        .replace('＃', '#')
        .replace('Ｓ', 'S')
        .uppercase(Locale.ROOT)

    private fun emptyRoomNaturalSortKey(room: String): String = Regex("\\d+")
        .replace(emptyRoomMatchKey(room)) { match -> match.value.padStart(7, '0') }

    private fun blendColors(base: Int, tint: Int, amount: Float): Int {
        val ratio = amount.coerceIn(0f, 1f)
        fun channel(baseChannel: Int, tintChannel: Int): Int =
            (baseChannel + (tintChannel - baseChannel) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(
            channel(Color.red(base), Color.red(tint)),
            channel(Color.green(base), Color.green(tint)),
            channel(Color.blue(base), Color.blue(tint))
        )
    }

    private fun onEmptyRoomFilterChanged() {
        emptyRoomRequestGeneration++
        emptyRoomsLoading = false
        emptyRoomLoadError = null
        refreshVisibleEmptyRooms()
    }

    private fun collapseEmptyRoomResultGroups(result: RemoteEmptyRoomResult) {
        val campusPrefix = "${result.campus}:"
        collapsedEmptyRoomGroups.removeAll { it.startsWith(campusPrefix) }
        groupEmptyRooms(result).forEach { group ->
            collapsedEmptyRoomGroups.add("${result.campus}:${group.title}")
        }
    }

    private fun refreshEmptyRooms() {
        if (currentMainSection != 3) return
        if (emptyRoomsLoading) return
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
        val password = preferences.getString(KEY_PASSWORD, "").orEmpty()
        val campus = emptyRoomCampus
        val week = emptyRoomWeek
        val weekday = emptyRoomWeekday
        val section = emptyRoomSectionCode
        val generation = ++emptyRoomRequestGeneration

        if (account.isBlank() || password.isBlank()) {
            emptyRoomsLoading = false
            emptyRoomLoadError = "登录信息不完整，请重新登录后再查询空教室。"
            emptyRoomQueryExpanded = false
            refreshVisibleEmptyRooms()
            return
        }

        emptyRoomsLoading = true
        emptyRoomLoadError = null
        refreshVisibleEmptyRooms()
        if (account == "114514") {
            val result = sampleEmptyRoomResult(campus, week, weekday, section)
            collapseEmptyRoomResultGroups(result)
            emptyRoomResult = result
            emptyRoomsLoading = false
            emptyRoomQueryExpanded = false
            refreshVisibleEmptyRooms()
            return
        }

        networkExecutor.execute {
            try {
                val result = SdauCourseRepository().queryEmptyRooms(
                    account, password, campus, week, weekday, section
                )
                runOnUiThread {
                    if (generation != emptyRoomRequestGeneration) return@runOnUiThread
                    collapseEmptyRoomResultGroups(result)
                    emptyRoomResult = result
                    emptyRoomsLoading = false
                    emptyRoomLoadError = null
                    emptyRoomQueryExpanded = false
                    refreshVisibleEmptyRooms()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (generation != emptyRoomRequestGeneration) return@runOnUiThread
                    emptyRoomsLoading = false
                    emptyRoomLoadError = error.message?.replace(Regex("\\s+"), " ")?.take(180)
                        ?: "教务系统暂时无法查询空教室，请稍后重试。"
                    emptyRoomQueryExpanded = false
                    refreshVisibleEmptyRooms()
                }
            }
        }
    }

    private fun refreshVisibleEmptyRooms() {
        if (currentMainSection != 3) return
        val host = mainSectionHost ?: return
        replaceMainSection(host, buildEmptyRoomSection(), 3, 0f, 170L)
    }

    private fun sampleEmptyRoomResult(
        campus: String,
        week: Int,
        weekday: Int,
        sectionCode: String
    ): RemoteEmptyRoomResult {
        val rooms = when (campus) {
            "泮河校区" -> listOf(
                "N104", "W205", "E308", "S514", "19#201", "19#403",
                "线上教学", "南校区体育羽毛球馆", "南校实践环节地点化学实践S"
            )
            "西北片区" -> listOf("22#205", "22#302", "22#402")
            else -> listOf(
                "5N101", "5N202", "5N306", "5S111", "5S416",
                "文理大楼503", "北校12号楼310", "线上教学"
            )
        }
        return RemoteEmptyRoomResult(selectedTerm(), week, campus, weekday, sectionCode, rooms)
    }

    private fun showEmptyRoomFilterPicker(
        title: String,
        options: List<Pair<String, String>>,
        selected: String,
        onSelected: (String) -> Unit
    ) {
        if (emptyRoomFilterOverlay != null) return
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(145, 12, 18, 30))
            isClickable = true
            setOnClickListener { hideEmptyRoomFilterPicker() }
        }
        val card = surfaceCard(dp(24f).toFloat()).apply {
            strokeWidth = 0
            setCardBackgroundColor(PAGE_BACKGROUND)
            setOnClickListener { }
        }
        val cardBody = verticalLayout().apply { setPadding(dp(18), dp(17), dp(18), dp(17)) }
        cardBody.addView(text(title, 20f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(12)))
        val optionList = verticalLayout()
        options.forEach { (label, value) ->
            val active = value == selected
            val row = horizontalLayout().apply {
                gravity = Gravity.CENTER_VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(11f).toFloat()
                    setColor(if (active) Color.rgb(238, 241, 255) else Color.TRANSPARENT)
                }
                setPadding(dp(13), dp(10), dp(10), dp(10))
                isClickable = true
                setOnClickListener {
                    hideEmptyRoomFilterPicker()
                    onSelected(value)
                }
            }
            row.addView(text(label, 14f, if (active) PRIMARY_DARK else TEXT_PRIMARY, if (active) Typeface.BOLD else Typeface.NORMAL), LinearLayout.LayoutParams(0, -2, 1f))
            if (active) row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_check)
                imageTintList = ColorStateList.valueOf(PRIMARY_DARK)
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            optionList.addView(row, spacedParams(dp(3)))
        }
        if (options.size > 8) {
            cardBody.addView(ScrollView(this).apply {
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(optionList, FrameLayout.LayoutParams(-1, -2))
            }, LinearLayout.LayoutParams(-1, minOf(dp(430), resources.displayMetrics.heightPixels - dp(150))))
        } else {
            cardBody.addView(optionList, matchWrapParams())
        }
        card.addView(cardBody)
        val width = minOf(dp(330), resources.displayMetrics.widthPixels - dp(36))
        overlay.addView(card, FrameLayout.LayoutParams(width, -2, Gravity.CENTER))
        pageHost.addView(overlay, matchParentParams())
        emptyRoomFilterOverlay = overlay
        overlay.alpha = 0f
        card.scaleX = .94f
        card.scaleY = .94f
        overlay.animate().alpha(1f).setDuration(150).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(190).start()
    }

    private fun hideEmptyRoomFilterPicker() {
        val overlay = emptyRoomFilterOverlay ?: return
        overlay.animate().alpha(0f).setDuration(130).withEndAction {
            pageHost.removeView(overlay)
            emptyRoomFilterOverlay = null
        }.start()
    }

    private fun emptyRoomWeekdayLabel(day: Int): String = when (day) {
        1 -> "星期一"
        2 -> "星期二"
        3 -> "星期三"
        4 -> "星期四"
        5 -> "星期五"
        6 -> "星期六"
        else -> "星期日"
    }

    private fun emptyRoomSectionLabel(code: String): String = when (code) {
        "0102" -> "第一大节"
        "0304" -> "第二大节"
        "0506" -> "第三大节"
        "0708" -> "第四大节"
        "0910" -> "第五大节"
        else -> code
    }

    private fun defaultEmptyRoomSection(now: Calendar = Calendar.getInstance()): String {
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val starts = currentStartMinutes()
        val codes = arrayOf("0102", "0304", "0506", "0708", "0910")
        codes.indices.forEach { sectionIndex ->
            val secondSlot = sectionIndex * 2 + 1
            val sectionEnd = starts[secondSlot] + 45
            if (minute <= sectionEnd) return codes[sectionIndex]
        }
        return "晚间"
    }

    private fun syncEmptyRoomDefaultsToNow() {
        val now = Calendar.getInstance()
        val termWeek = weekForTerm(selectedTerm())
        emptyRoomWeek = if (termWeek <= 0) 1 else termWeek
        emptyRoomWeekday = now.get(Calendar.DAY_OF_WEEK).let { day ->
            if (day == Calendar.SUNDAY) 7 else day - 1
        }
        emptyRoomSectionCode = defaultEmptyRoomSection(now)
    }

    private fun buildScoreResultSection(result: RemoteScoreResult): View {
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.TRANSPARENT)
        }
        val body = verticalLayout().apply { setPadding(dp(20), dp(18), dp(20), dp(28)) }
        addGradeHeader(body, result.term)

        val summaryCard = surfaceCard(dp(24f).toFloat()).apply {
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(Color.argb(176, 250, 252, 255))
        }
        val metrics = horizontalLayout().apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(18), dp(12), dp(18))
        }
        metrics.addView(scoreMetric("平均成绩", result.averageScore, Color.rgb(245, 108, 126)), LinearLayout.LayoutParams(0, -2, 1f))
        metrics.addView(scoreMetric("平均绩点", result.averageCreditGpa, Color.rgb(131, 140, 199)), LinearLayout.LayoutParams(0, -2, 1f))
        metrics.addView(scoreMetric("总学分", result.totalCredits, PRIMARY_DARK), LinearLayout.LayoutParams(0, -2, 1f))
        summaryCard.addView(metrics)
        body.addView(summaryCard, spacedParams(dp(18)))

        result.records.forEach { record ->
            val card = surfaceCard(dp(20f).toFloat()).apply {
                cardElevation = 0f
                strokeWidth = 0
                setCardBackgroundColor(Color.argb(164, 250, 252, 255))
            }
            val row = horizontalLayout().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(17), dp(15), dp(17), dp(15))
            }
            val course = verticalLayout()
            course.addView(text(record.courseName.ifBlank { "未命名课程" }, 16f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(7)))
            val details = buildList {
                if (record.courseCode.isNotBlank()) add(record.courseCode)
                if (record.credit.isNotBlank()) add("${record.credit} 学分")
            }.joinToString("  ·  ")
            course.addView(text(details.ifBlank { "课程成绩" }, 12f, TEXT_SECONDARY, Typeface.NORMAL), matchWrapParams())
            row.addView(course, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(12) })
            row.addView(text(record.score.ifBlank { "-" }, 22f, scoreColor(record.score), Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = "查看${record.courseName}成绩构成"
                setOnClickListener { showScoreDetail(record) }
            }, LinearLayout.LayoutParams(dp(58), dp(48)))
            card.addView(row)
            body.addView(card, spacedParams(dp(10)))
        }
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        return scroll
    }

    private fun scoreMetric(label: String, value: String, valueColor: Int): View = verticalLayout().apply {
        gravity = Gravity.CENTER
        addView(text(value.ifBlank { "-" }, 21f, valueColor, Typeface.BOLD).apply { gravity = Gravity.CENTER }, spacedParams(dp(5)))
        addView(text(label, 11f, TEXT_SECONDARY, Typeface.NORMAL).apply { gravity = Gravity.CENTER }, matchWrapParams())
    }

    private fun scoreColor(value: String): Int {
        val number = value.trim().toDoubleOrNull()
        return when {
            number != null && number < 60 -> ERROR
            number != null && number >= 80 -> Color.rgb(41, 132, 91)
            number != null -> Color.rgb(177, 117, 28)
            value.contains("不及格") || value.contains("不合格") -> ERROR
            else -> PRIMARY_DARK
        }
    }

    private fun exportScoreImage(term: String) {
        if (scoreExporting) return
        val result = loadScoreCache()?.takeIf { it.term == term && it.records.isNotEmpty() }
        if (result == null) {
            Toast.makeText(this, "暂无可导出的成绩", Toast.LENGTH_SHORT).show()
            return
        }

        scoreExporting = true
        Toast.makeText(this, "正在生成成绩图片…", Toast.LENGTH_SHORT).show()
        networkExecutor.execute {
            var bitmap: Bitmap? = null
            try {
                bitmap = createScoreBitmap(result)
                saveScoreBitmap(bitmap, result.term)
                runOnUiThread {
                    Toast.makeText(this, "成绩图片已保存到 Pictures/WeSDAU", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "保存成绩图片失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            } finally {
                bitmap?.recycle()
                scoreExporting = false
            }
        }
    }

    private fun createScoreBitmap(result: RemoteScoreResult): Bitmap {
        val width = 1600
        val padding = 44
        val headerHeight = 170
        val summaryHeight = 120
        val tableTitleHeight = 48
        val tableHeaderHeight = 56
        val rowHeight = 68
        val rows = result.records.ifEmpty {
            listOf(RemoteScore("-", "当前开课时间暂无成绩记录", "-", "-", "-"))
        }
        val height = padding * 2 + headerHeight + 18 + summaryHeight + 18 +
            tableTitleHeight + tableHeaderHeight + rows.size * rowHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Bitmap 默认是透明的，深色图片查看器会将透明区域显示为黑色，
        // 从而让深色标题和表格内容看起来像“丢失”。导出图使用固定浅色底，
        // 保证在浅色/深色系统主题和不同图片查看器中都保持一致。
        canvas.drawColor(Color.rgb(243, 249, 252))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        val contentWidth = width - padding * 2

        fun roundedRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) {
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawRoundRect(RectF(x, y, x + w, y + h), radius, radius, paint)
        }

        fun drawText(value: String, x: Float, baseline: Float, size: Float, color: Int, style: Int) {
            paint.style = Paint.Style.FILL
            paint.color = color
            paint.textSize = size
            paint.typeface = Typeface.create("sans-serif", style)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(value, x, baseline, paint)
        }

        fun wrap(value: String, maxWidth: Float, size: Float, style: Int): List<String> {
            val safe = value.ifBlank { "-" }
            paint.textSize = size
            paint.typeface = Typeface.create("sans-serif", style)
            val lines = mutableListOf<String>()
            var current = ""
            safe.forEach { character ->
                val next = current + character
                if (current.isNotEmpty() && paint.measureText(next) > maxWidth) {
                    lines += current
                    current = character.toString()
                } else {
                    current = next
                }
            }
            if (current.isNotEmpty()) lines += current
            return lines.ifEmpty { listOf("-") }
        }

        paint.shader = LinearGradient(
            padding.toFloat(), padding.toFloat(),
            (padding + contentWidth).toFloat(), (padding + headerHeight).toFloat(),
            Color.rgb(232, 248, 255), Color.rgb(245, 239, 255), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            RectF(padding.toFloat(), padding.toFloat(), (padding + contentWidth).toFloat(), (padding + headerHeight).toFloat()),
            24f, 24f, paint
        )
        paint.shader = null

        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty().ifBlank { "-" }
        val studentName = preferences.getString(KEY_STUDENT_NAME, "").orEmpty().trim()
        val displayName = if (studentName.isBlank() || account == "-") account else "$studentName-$account"
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.textSize = 24f
        paint.color = Color.rgb(79, 107, 121)
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        canvas.drawText("WeSDAU-成绩单", (padding + 24).toFloat(), (padding + 44).toFloat(), paint)
        drawText(displayName, (padding + 24).toFloat(), (padding + 98).toFloat(), 40f, Color.rgb(23, 51, 63), Typeface.BOLD)
        drawText("学期：${result.term.ifBlank { "-" }}", (padding + 24).toFloat(), (padding + 136).toFloat(), 24f, Color.rgb(87, 115, 130), Typeface.NORMAL)

        var y = padding + headerHeight + 18
        val summaryGap = 18
        val summaryWidth = (contentWidth - summaryGap * 2) / 3f
        roundedRect(padding.toFloat(), y.toFloat(), summaryWidth, summaryHeight.toFloat(), 18f, Color.rgb(255, 245, 248))
        drawText("平均成绩", (padding + 20).toFloat(), (y + 38).toFloat(), 22f, Color.rgb(108, 128, 144), Typeface.NORMAL)
        drawText(result.averageScore.ifBlank { "-" }, (padding + 20).toFloat(), (y + 92).toFloat(), 44f, Color.rgb(245, 108, 126), Typeface.BOLD)

        val gpaX = padding + summaryWidth + summaryGap
        roundedRect(gpaX, y.toFloat(), summaryWidth, summaryHeight.toFloat(), 18f, Color.rgb(245, 244, 255))
        drawText("平均学分绩点", gpaX + 20, (y + 38).toFloat(), 22f, Color.rgb(108, 128, 144), Typeface.NORMAL)
        drawText(result.averageCreditGpa.ifBlank { "-" }, gpaX + 20, (y + 92).toFloat(), 44f, Color.rgb(131, 140, 199), Typeface.BOLD)

        val metaX = gpaX + summaryWidth + summaryGap
        roundedRect(metaX, y.toFloat(), summaryWidth, summaryHeight.toFloat(), 18f, Color.rgb(247, 252, 255))
        drawText("课程统计", metaX + 20, (y + 38).toFloat(), 22f, Color.rgb(95, 119, 131), Typeface.NORMAL)
        val countText = "门数：${result.records.size}"
        drawText(countText, metaX + 20, (y + 88).toFloat(), 28f, Color.rgb(31, 61, 75), Typeface.BOLD)
        paint.textSize = 28f
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        val countWidth = paint.measureText(countText)
        drawText("总学分：${result.totalCredits.ifBlank { "-" }}", metaX + 20 + countWidth + 22, (y + 88).toFloat(), 20f, Color.rgb(31, 61, 75), Typeface.NORMAL)

        y += summaryHeight + 18
        drawText("课程成绩", (padding + 2).toFloat(), (y + 34).toFloat(), 30f, Color.rgb(36, 70, 86), Typeface.BOLD)
        y += tableTitleHeight

        val ratios = floatArrayOf(1.2f, 2.3f, .8f, .8f, .8f)
        val ratioSum = ratios.sum()
        val columnWidths = ratios.map { contentWidth * it / ratioSum }
        val headers = listOf("课程代码", "课程名", "学分", "总成绩", "绩点")
        roundedRect(padding.toFloat(), y.toFloat(), contentWidth.toFloat(), tableHeaderHeight.toFloat(), 14f, Color.rgb(234, 244, 250))
        var x = padding.toFloat()
        headers.forEachIndexed { index, header ->
            drawText(header, x + 14, (y + 35).toFloat(), 21f, Color.rgb(80, 105, 119), Typeface.BOLD)
            x += columnWidths[index]
        }
        y += tableHeaderHeight

        rows.forEachIndexed { index, record ->
            val rowY = y + index * rowHeight
            roundedRect(padding.toFloat(), (rowY + 3).toFloat(), contentWidth.toFloat(), (rowHeight - 6).toFloat(), 12f, if (index % 2 == 0) Color.WHITE else Color.rgb(248, 252, 255))
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = Color.rgb(230, 239, 244)
            canvas.drawLine(padding.toFloat(), (rowY + rowHeight).toFloat(), (padding + contentWidth).toFloat(), (rowY + rowHeight).toFloat(), paint)

            val values = listOf(record.courseCode, record.courseName, record.credit, record.score, record.gpa)
            var cellX = padding.toFloat()
            values.forEachIndexed { column, rawValue ->
                val value = rawValue.ifBlank { "-" }
                val columnWidth = columnWidths[column]
                if (column == 1) {
                    val lines = wrap(value, columnWidth - 28, 22f, Typeface.BOLD).take(2)
                    val startY = rowY + if (lines.size == 2) 27 else 42
                    lines.forEachIndexed { lineIndex, line ->
                        drawText(line, cellX + 14, (startY + lineIndex * 24).toFloat(), 22f, Color.rgb(25, 55, 68), Typeface.BOLD)
                    }
                } else {
                    val color = if (column == 3) scoreColor(value) else Color.rgb(31, 61, 75)
                    val size = if (column == 0) 20f else 22f
                    val line = wrap(value, columnWidth - 28, size, Typeface.BOLD).first()
                    drawText(line, cellX + 14, (rowY + 42).toFloat(), size, color, Typeface.BOLD)
                }
                cellX += columnWidth
            }
        }
        return bitmap
    }

    private fun saveScoreBitmap(bitmap: Bitmap, term: String) {
        val safeTerm = term.replace(Regex("[^\\w\\u4e00-\\u9fa5-]"), "_")
        val displayName = "课程成绩-$safeTerm.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/WeSDAU")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建图片文件")
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "图片编码失败" }
                } ?: error("无法写入图片文件")
                contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            } catch (error: Exception) {
                contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            val directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.apply { mkdirs() }
                ?: error("无法访问图片目录")
            File(directory, displayName).outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "图片编码失败" }
            }
        }
    }

    private fun showScoreDetail(record: RemoteScore) {
        if (scoreDetailOverlay != null) return
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(150, 12, 18, 30))
            isClickable = true
            setOnClickListener { hideScoreDetail() }
        }
        val card = surfaceCard(dp(25f).toFloat()).apply {
            cardElevation = dp(3).toFloat()
            strokeWidth = 0
            setCardBackgroundColor(Color.rgb(250, 252, 254))
            setOnClickListener { }
        }
        val body = verticalLayout().apply { setPadding(dp(18), dp(17), dp(18), dp(18)) }
        val header = horizontalLayout().apply { gravity = Gravity.CENTER_VERTICAL }
        val titleGroup = verticalLayout()
        titleGroup.addView(text("成绩构成", 13f, TEXT_SECONDARY, Typeface.NORMAL), spacedParams(dp(5)))
        titleGroup.addView(text(
            listOf(record.courseName, record.courseCode).filter { it.isNotBlank() }.joinToString("-").ifBlank { "课程成绩" },
            20f, TEXT_PRIMARY, Typeface.BOLD
        ), matchWrapParams())
        header.addView(titleGroup, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(10) })
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            contentDescription = "关闭"
            imageTintList = ColorStateList.valueOf(TEXT_SECONDARY)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.rgb(241, 245, 248))
            }
            setOnClickListener { hideScoreDetail() }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        body.addView(header, spacedParams(dp(16)))

        val content = FrameLayout(this)
        content.addView(android.widget.ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(PRIMARY)
            contentDescription = "加载成绩构成"
        }, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER))
        body.addView(content, LinearLayout.LayoutParams(-1, dp(220)))
        card.addView(body)
        val width = minOf(dp(370), resources.displayMetrics.widthPixels - dp(24))
        overlay.addView(card, FrameLayout.LayoutParams(width, -2, Gravity.CENTER))
        pageHost.addView(overlay, matchParentParams())
        scoreDetailOverlay = overlay
        overlay.alpha = 0f
        card.scaleX = .94f
        card.scaleY = .94f
        overlay.animate().alpha(1f).setDuration(150).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(190).start()

        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
        val password = preferences.getString(KEY_PASSWORD, "").orEmpty()
        networkExecutor.execute {
            val result = if (account == "114514") {
                Result.success(sampleScoreDetail(record))
            } else {
                runCatching { SdauCourseRepository().queryScoreDetail(account, password, record) }
            }
            runOnUiThread {
                if (scoreDetailOverlay !== overlay) return@runOnUiThread
                content.removeAllViews()
                result.onSuccess { detail ->
                    content.addView(buildScoreDetailContent(detail), FrameLayout.LayoutParams(-1, -2))
                    val params = content.layoutParams
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    content.layoutParams = params
                }.onFailure { error ->
                    content.addView(text(
                        error.message?.replace(Regex("\\s+"), " ")?.take(150) ?: "成绩构成查询失败",
                        14f, ERROR, Typeface.NORMAL
                    ).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, dp(120), Gravity.CENTER))
                    val params = content.layoutParams
                    params.height = dp(120)
                    content.layoutParams = params
                }
            }
        }
    }

    private fun buildScoreDetailContent(detail: RemoteScoreDetail): View {
        val content = verticalLayout()
        val total = MaterialCardView(this).apply {
            radius = dp(20f).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(Color.rgb(243, 248, 251))
        }
        val totalRow = horizontalLayout().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
        }
        totalRow.addView(text("总成绩", 16f, TEXT_SECONDARY, Typeface.BOLD), LinearLayout.LayoutParams(0, -2, 1f))
        totalRow.addView(text(detail.totalScore, 30f, scoreColor(detail.totalScore), Typeface.BOLD), LinearLayout.LayoutParams(-2, -2))
        total.addView(totalRow)
        content.addView(total, spacedParams(dp(12)))

        val firstRow = horizontalLayout()
        firstRow.addView(scoreDetailCell("平时成绩", detail.usualScore), LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        firstRow.addView(scoreDetailCell("平时占比", formatScoreRatio(detail.usualRatio)), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        content.addView(firstRow, spacedParams(dp(12)))
        val secondRow = horizontalLayout()
        secondRow.addView(scoreDetailCell("期末成绩", detail.finalScore), LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        secondRow.addView(scoreDetailCell("期末占比", formatScoreRatio(detail.finalRatio)), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        content.addView(secondRow, matchWrapParams())
        return content
    }

    private fun scoreDetailCell(label: String, value: String): View = MaterialCardView(this).apply {
        radius = dp(20f).toFloat()
        cardElevation = 0f
        strokeWidth = 0
        setCardBackgroundColor(Color.rgb(243, 248, 251))
        addView(verticalLayout().apply {
            setPadding(dp(15), dp(13), dp(15), dp(13))
            addView(text(label, 14f, TEXT_SECONDARY, Typeface.NORMAL), spacedParams(dp(7)))
            addView(text(value.ifBlank { "-" }, 23f, TEXT_PRIMARY, Typeface.BOLD), matchWrapParams())
        })
    }

    private fun formatScoreRatio(value: String): String {
        val clean = value.trim()
        return if (clean.isNotBlank() && clean != "-" && !clean.contains("%")) "$clean%" else clean.ifBlank { "-" }
    }

    private fun hideScoreDetail() {
        val overlay = scoreDetailOverlay ?: return
        overlay.animate().alpha(0f).setDuration(130).withEndAction {
            pageHost.removeView(overlay)
            scoreDetailOverlay = null
        }.start()
    }

    private fun addGradeHeader(body: LinearLayout, term: String) {
        val header = horizontalLayout().apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("成绩", 28f, TEXT_PRIMARY, Typeface.BOLD), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_export)
            imageTintList = ColorStateList.valueOf(PRIMARY_DARK)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            contentDescription = "保存成绩图片"
            isEnabled = !scoreExporting
            setOnClickListener { exportScoreImage(term) }
        }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(6) })
        val selector = MaterialCardView(this).apply {
            radius = dp(14f).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(Color.rgb(246, 248, 252))
            isClickable = true
            contentDescription = "选择成绩学期，当前为 $term"
            setOnClickListener { showScoreTermPicker() }
        }
        val selectorContent = horizontalLayout().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(7), dp(9), dp(7))
        }
        selectorContent.addView(text(term, 12f, TEXT_PRIMARY, Typeface.NORMAL), LinearLayout.LayoutParams(-2, -2))
        selectorContent.addView(text("⌄", 15f, TEXT_SECONDARY, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
            setPadding(dp(5), 0, 0, dp(2))
        }, LinearLayout.LayoutParams(dp(20), -2))
        selector.addView(selectorContent)
        header.addView(selector, LinearLayout.LayoutParams(-2, -2))
        body.addView(header, spacedParams(dp(18)))
    }

    private fun buildGradeStateSection(
        term: String,
        hasLoadedResult: Boolean,
        error: String?,
        emptyTitle: String = "暂无成绩信息",
        emptyDescription: String = "本学期暂未发布课程成绩\n成绩公布后会自动显示在这里"
    ): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.TRANSPARENT)
        }
        val body = verticalLayout().apply { setPadding(dp(20), dp(18), dp(20), dp(28)) }
        addGradeHeader(body, term)
        val state = when {
            error != null -> verticalLayout().apply {
                gravity = Gravity.CENTER
                setPadding(dp(22), dp(20), dp(22), dp(20))
                addView(text("!", 22f, ERROR, Typeface.BOLD).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(52), dp(44)).apply { bottomMargin = dp(10) })
                addView(text(error, 14f, TEXT_SECONDARY, Typeface.NORMAL).apply {
                    gravity = Gravity.CENTER
                    setLineSpacing(dp(4).toFloat(), 1f)
                }, matchWrapParams())
            }
            hasLoadedResult -> buildAcademicEmptyState(
                EmptyAcademicState.GRADES,
                emptyTitle,
                emptyDescription
            )
            else -> verticalLayout().apply {
                gravity = Gravity.CENTER
                addView(ProgressBar(this@MainActivity).apply {
                    isIndeterminate = true
                    indeterminateTintList = ColorStateList.valueOf(PRIMARY)
                    contentDescription = "加载成绩"
                }, LinearLayout.LayoutParams(dp(38), dp(38)))
            }
        }
        body.addView(state, LinearLayout.LayoutParams(-1, 0, 1f))
        scroll.addView(body, FrameLayout.LayoutParams(-1, -1))
        return scroll
    }

    private fun showScoreTermPicker() {
        if (scoreTermOverlay != null) return
        val selected = selectedScoreTerm()
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(145, 12, 18, 30))
            isClickable = true
            setOnClickListener { hideScoreTermPicker() }
        }
        val card = surfaceCard(dp(24f).toFloat()).apply {
            strokeWidth = 0
            setCardBackgroundColor(PAGE_BACKGROUND)
            setOnClickListener { }
        }
        val body = verticalLayout().apply { setPadding(dp(18), dp(17), dp(18), dp(17)) }
        body.addView(text("选择成绩学期", 20f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(13)))
        scoreTermOptions().forEach { term ->
            val active = term == selected
            val row = horizontalLayout().apply {
                gravity = Gravity.CENTER_VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(11f).toFloat()
                    setColor(if (active) Color.rgb(238, 241, 255) else Color.TRANSPARENT)
                }
                setPadding(dp(13), dp(10), dp(10), dp(10))
                setOnClickListener {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_SCORE_TERM, term).apply()
                    scoreLoadError = null
                    hideScoreTermPicker()
                    refreshVisibleGrades()
                    refreshScores()
                }
            }
            row.addView(text(term, 14f, if (active) PRIMARY_DARK else TEXT_PRIMARY, if (active) Typeface.BOLD else Typeface.NORMAL), LinearLayout.LayoutParams(0, -2, 1f))
            if (active) row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_check)
                imageTintList = ColorStateList.valueOf(PRIMARY_DARK)
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            body.addView(row, spacedParams(dp(3)))
        }
        card.addView(body)
        val width = minOf(dp(330), resources.displayMetrics.widthPixels - dp(36))
        overlay.addView(card, FrameLayout.LayoutParams(width, -2, Gravity.CENTER))
        pageHost.addView(overlay, matchParentParams())
        scoreTermOverlay = overlay
        overlay.alpha = 0f
        card.scaleX = .94f
        card.scaleY = .94f
        overlay.animate().alpha(1f).setDuration(150).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(190).start()
    }

    private fun hideScoreTermPicker() {
        val overlay = scoreTermOverlay ?: return
        overlay.animate().alpha(0f).setDuration(130).withEndAction {
            pageHost.removeView(overlay)
            scoreTermOverlay = null
        }.start()
    }

    private fun buildDataSection(
        title: String,
        summary: String,
        symbol: String,
        emptyTitle: String,
        emptyDescription: String
    ): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.TRANSPARENT)
        }
        val body = verticalLayout().apply {
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }
        body.addView(text(title, 28f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(7)))
        body.addView(text(summary, 13f, TEXT_SECONDARY, Typeface.NORMAL), spacedParams(dp(24)))
        val card = surfaceCard(dp(26f).toFloat()).apply {
            cardElevation = 0f
            setCardBackgroundColor(Color.argb(214, 255, 255, 255))
        }
        val empty = verticalLayout().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(42), dp(28), dp(42))
        }
        empty.addView(text(symbol, 22f, PRIMARY_DARK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(PRIMARY_CONTAINER)
                setStroke(dp(1), Color.argb(150, 255, 255, 255))
            }
        }, LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(20) })
        empty.addView(text(emptyTitle, 19f, TEXT_PRIMARY, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }, spacedParams(dp(10)))
        empty.addView(text(emptyDescription, 14f, TEXT_SECONDARY, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
        }, matchWrapParams())
        card.addView(empty)
        body.addView(card, matchWrapParams())
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        return scroll
    }

    private fun buildScheduleHeader(): View {
        val header = verticalLayout().apply {
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(20), dp(10), dp(4), dp(8))
        }
        val row = horizontalLayout().apply { gravity = Gravity.CENTER_VERTICAL }
        val group = verticalLayout()
        scheduleDate = fixedAdaptiveText(
            if (viewingPublicSchedule) publicScheduleClassName else todayLabel(),
            26f,
            20f,
            TEXT_PRIMARY,
            Typeface.BOLD
        )
        scheduleWeek = fixedAdaptiveText(formatWeekLabel(currentWeek), 13f, 11f, TEXT_PRIMARY, Typeface.NORMAL)
        group.addView(scheduleDate, spacedParams(dp(7)))
        group.addView(scheduleWeek, matchWrapParams())
        row.addView(group, LinearLayout.LayoutParams(0, -2, 1f))
        pushButton = ImageButton(this).apply {
            setImageResource(if (pushEnabled) R.drawable.ic_push_on else R.drawable.ic_push_off)
            contentDescription = if (pushEnabled) "关闭课程推送" else "开启课程推送"
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { togglePushNotifications() }
        }
        if (viewingPublicSchedule) pushButton?.visibility = View.GONE
        row.addView(pushButton, LinearLayout.LayoutParams(dp(48), dp(44)))
        row.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_schedule_mode)
            contentDescription = "选择作息时间"
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { showScheduleModePicker() }
        }, LinearLayout.LayoutParams(dp(48), dp(44)))
        row.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_share)
            contentDescription = "分享"
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { showSharePicker() }
        }, LinearLayout.LayoutParams(dp(48), dp(44)))
        row.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_update_lightning)
            contentDescription = "检查更新或重新登录"
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { showUpdateMenu(this) }
        }, LinearLayout.LayoutParams(dp(56), dp(44)))
        header.addView(row, spacedParams(dp(6)))
        updatePushButton()
        return header
    }

    private fun showUpdateMenu(anchor: View, fromBottom: Boolean = false) {
        val panel = verticalLayout().apply {
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        var popup: PopupWindow? = null
        fun addAction(label: String, dismissOnClick: Boolean = true, action: () -> Unit) {
            panel.addView(text(label, 13f, TEXT_PRIMARY, Typeface.NORMAL).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(7), dp(10), dp(7))
                isClickable = true
                setOnClickListener { if (dismissOnClick) popup?.dismiss(); action() }
            }, LinearLayout.LayoutParams(-1, dp(36)))
        }
        addAction("检查更新", false) {
            panel.removeAllViews()
            panel.addView(text("正在检查更新…", 12f, TEXT_SECONDARY, Typeface.NORMAL).apply {
                setPadding(dp(10), dp(8), dp(10), dp(8))
            })
            networkExecutor.execute {
                val update = runCatching { readRemoteUpdate() }.getOrNull()
                runOnUiThread {
                    panel.removeAllViews()
                    if (update == null) {
                        panel.addView(text("检查失败，请稍后重试", 12f, TEXT_SECONDARY, Typeface.NORMAL).apply {
                            setPadding(dp(10), dp(8), dp(10), dp(8))
                        })
                    } else if (update.code <= currentVersionCode) {
                        panel.addView(text("已是最新版本 $appDisplayVersion", 12f, TEXT_SECONDARY, Typeface.NORMAL).apply {
                            setPadding(dp(10), dp(8), dp(10), dp(8))
                        })
                    } else {
                        popup?.dismiss()
                        showUpdateDialog(update)
                        return@runOnUiThread
                    }
                    addAction("重新登录") { showLoginPage(true) }
                }
            }
        }
        addAction("重新登录") { showLoginPage(true) }
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = dp(2).toFloat()
            strokeWidth = dp(1)
            setStrokeColor(OUTLINE)
            setCardBackgroundColor(SURFACE)
            addView(panel)
        }
        popup = PopupWindow(card, dp(216), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(8).toFloat()
            isOutsideTouchable = true
        }
        if (fromBottom) {
            popup?.showAtLocation(anchor, Gravity.BOTTOM or Gravity.END, dp(18), dp(92))
        } else {
            popup?.showAsDropDown(anchor, -dp(164), dp(4))
        }
    }

    private fun buildExportCoursePlacements(visibleCourses: List<Course>): List<CoursePlacement> {
        val result = mutableListOf<CoursePlacement>()

        fun overlaps(first: Course, second: Course): Boolean {
            if (first.day != second.day) return false
            val firstEnd = first.startSlot + first.slotCount
            val secondEnd = second.startSlot + second.slotCount
            return first.startSlot < secondEnd && second.startSlot < firstEnd
        }

        visibleCourses.filter { it.day in 0..6 && it.startSlot in 0..9 }
            .groupBy { it.day }
            .values
            .forEach { dayCourses ->
                val sorted = dayCourses.sortedWith(
                    compareBy<Course> { it.startSlot }
                        .thenByDescending { it.slotCount }
                )
                val component = mutableListOf<Course>()

                fun flushComponent() {
                    if (component.isEmpty()) return
                    val columnEnds = mutableListOf<Int>()
                    val assigned = mutableListOf<Pair<Course, Int>>()
                    component.forEach { course ->
                        val column = columnEnds.indexOfFirst { end -> end <= course.startSlot }
                            .let { if (it >= 0) it else columnEnds.size }
                        if (column == columnEnds.size) columnEnds += 0
                        columnEnds[column] = course.startSlot + course.slotCount
                        assigned += course to column
                    }
                    val columnCount = columnEnds.size
                    assigned.forEach { (course, column) ->
                        result += CoursePlacement(course, column, columnCount)
                    }
                    component.clear()
                }

                sorted.forEach { course ->
                    if (component.isNotEmpty() && component.none { overlaps(it, course) }) {
                        flushComponent()
                    }
                    component += course
                }
                flushComponent()
            }
        return result
    }

    private fun createScheduleBitmap(
        term: String,
        week: Int,
        mode: ScheduleMode,
        courses: List<Course>,
        includeAllWeeks: Boolean = false
    ): Bitmap {
        val width = 2048
        val height = 1152
        val padding = 40f
        val gridTop = 150f
        val gridHeight = 1000f
        val gridWidth = width - padding * 2
        val timeColumnWidth = 196f
        val dayColumnWidth = (gridWidth - timeColumnWidth) / 7f
        val headerHeight = 166f
        val rowHeight = (gridHeight - headerHeight) / 5f
        val background = Color.rgb(243, 249, 252)
        val gridFill = Color.rgb(250, 253, 255)
        val headerFill = Color.rgb(239, 248, 252)
        val gridLine = Color.rgb(211, 229, 238)
        val primary = Color.rgb(23, 51, 63)
        val secondary = Color.rgb(87, 115, 130)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        canvas.drawColor(background)

        fun setText(size: Float, color: Int, style: Int, align: Paint.Align = Paint.Align.LEFT) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.textSize = size
            paint.color = color
            paint.typeface = Typeface.create("sans-serif", style)
            paint.textAlign = align
        }

        fun drawText(value: String, x: Float, baseline: Float, size: Float, color: Int, style: Int) {
            setText(size, color, style)
            canvas.drawText(value, x, baseline, paint)
        }

        fun drawCenteredText(value: String, centerX: Float, centerY: Float, size: Float, color: Int, style: Int) {
            setText(size, color, style, Paint.Align.CENTER)
            val metrics = paint.fontMetrics
            canvas.drawText(value, centerX, centerY - (metrics.ascent + metrics.descent) / 2f, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        fun roundedRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = color
            canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
        }

        fun wrapText(value: String, maxWidth: Float, size: Float, style: Int): List<String> {
            setText(size, primary, style)
            val result = mutableListOf<String>()
            value.ifBlank { "-" }.split('\n').forEach { paragraph ->
                var remaining = paragraph.ifBlank { "-" }
                while (remaining.isNotEmpty()) {
                    val count = paint.breakText(remaining, true, maxWidth, null)
                    if (count <= 0) break
                    result += remaining.substring(0, count)
                    remaining = remaining.substring(count)
                }
            }
            return result.ifEmpty { listOf("-") }
        }

        fun timeLabel(value: String): String = value.removePrefix("0")

        fun lightCourseColor(color: Int): Int = Color.rgb(
            (Color.red(color) + (255 - Color.red(color)) * .84f).toInt(),
            (Color.green(color) + (255 - Color.green(color)) * .84f).toInt(),
            (Color.blue(color) + (255 - Color.blue(color)) * .84f).toInt()
        )

        fun courseTextColor(color: Int): Int = Color.rgb(
            (Color.red(color) * .42f).toInt().coerceIn(30, 110),
            (Color.green(color) * .42f).toInt().coerceIn(30, 110),
            (Color.blue(color) * .42f).toInt().coerceIn(30, 110)
        )

        val gridLeft = padding
        val gridRight = padding + gridWidth
        val gridBottom = gridTop + gridHeight
        roundedRect(gridLeft, gridTop, gridRight, gridBottom, 16f, gridFill)
        canvas.save()
        val clipPath = Path().apply {
            addRoundRect(RectF(gridLeft, gridTop, gridRight, gridBottom), 16f, 16f, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
        paint.style = Paint.Style.FILL
        paint.color = headerFill
        canvas.drawRect(gridLeft, gridTop, gridRight, gridTop + headerHeight, paint)
        canvas.restore()

        // 标题和副标题沿用示例图的宽屏留白比例；全校模式导出班级完整课表，
        // 因此副标题显示班级名而不是当前周次。
        setText(54f, primary, Typeface.NORMAL)
        paint.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        canvas.drawText("WeSDAU-课程表", padding, 96f, paint)
        drawText(
            if (includeAllWeeks) "$term    $publicScheduleClassName"
            else "$term    ${if (week > 0) "第${week}周" else "学期未开始"}",
            padding,
            136f,
            30f,
            secondary,
            Typeface.NORMAL
        )

        // 网格线
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = gridLine
        for (column in 0..7) {
            val x = gridLeft + if (column == 0) 0f else timeColumnWidth + (column - 1) * dayColumnWidth
            canvas.drawLine(x, gridTop, x, gridBottom, paint)
        }
        for (row in 0..5) {
            val y = gridTop + if (row == 0) 0f else headerHeight + (row - 1) * rowHeight
            canvas.drawLine(gridLeft, y, gridRight, y, paint)
        }
        canvas.drawRoundRect(RectF(gridLeft, gridTop, gridRight, gridBottom), 16f, 16f, paint)

        val headerCenterY = gridTop + headerHeight / 2f
        drawCenteredText("节次", gridLeft + timeColumnWidth / 2f, headerCenterY, 31f, Color.rgb(42, 77, 92), Typeface.BOLD)
        arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").forEachIndexed { index, label ->
            val centerX = gridLeft + timeColumnWidth + index * dayColumnWidth + dayColumnWidth / 2f
            drawCenteredText(label, centerX, headerCenterY, 31f, Color.rgb(42, 77, 92), Typeface.BOLD)
        }

        val timeRanges = if (mode == ScheduleMode.SPRING) springTimeRanges() else summerTimeRanges()
        for (row in 0 until 5) {
            val top = gridTop + headerHeight + row * rowHeight
            val centerX = gridLeft + timeColumnWidth / 2f
            drawCenteredText("第${row + 1}大节", centerX, top + rowHeight * .42f, 32f, Color.rgb(48, 82, 96), Typeface.BOLD)
            drawCenteredText(
                "${timeLabel(timeRanges[row * 2].first)}-${timeLabel(timeRanges[row * 2 + 1].second)}",
                centerX,
                top + rowHeight * .66f,
                22f,
                secondary,
                Typeface.NORMAL
            )
        }

        val visibleCourses = if (includeAllWeeks) courses else courses.filter { courseVisibleInWeek(it, week) }
        buildExportCoursePlacements(visibleCourses).forEach { placement ->
            val course = placement.course
            val start = course.startSlot / 2f
            val end = ((course.startSlot + course.slotCount).coerceAtMost(10)) / 2f
            val baseLeft = gridLeft + timeColumnWidth + course.day * dayColumnWidth + 7f
            val totalWidth = dayColumnWidth - 14f
            val cardGap = if (placement.columnCount > 1) 4f else 0f
            val cardWidth = (totalWidth - cardGap * (placement.columnCount - 1)) /
                placement.columnCount.coerceAtLeast(1)
            val left = baseLeft + placement.column * (cardWidth + cardGap)
            val right = left + cardWidth
            val top = gridTop + headerHeight + start * rowHeight + 9f
            val bottom = gridTop + headerHeight + end * rowHeight - 9f
            if (bottom <= top) return@forEach

            val fillColor = lightCourseColor(course.background)
            val textColor = courseTextColor(course.background)
            roundedRect(left, top, right, bottom, 14f, fillColor)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = lightCourseColor(course.background).let {
                Color.rgb(
                    (Color.red(it) * .92f).toInt(),
                    (Color.green(it) * .92f).toInt(),
                    (Color.blue(it) * .92f).toInt()
                )
            }
            canvas.drawRoundRect(RectF(left, top, right, bottom), 14f, 14f, paint)

            val textLeft = left + 11f
            val maxTextWidth = right - left - 22f
            // 课程名保持示例图的醒目粗体；教室和教师连续排列，避免导出图浪费空间。
            val titleLines = wrapText(course.name, maxTextWidth, 25f, Typeface.BOLD).take(2)
            val detailLines = buildList {
                addAll(formatExportRoom(course.room).split('\n').filter { it.isNotBlank() })
                // 完整专业课表中，周数比教师名更重要；空间不足时 take(6) 会优先保留周数。
                if (includeAllWeeks && course.weeks.isNotBlank()) add("第${course.weeks}周")
                if (course.teacher.isNotBlank()) add(course.teacher)
            }.flatMap { line ->
                if (line.isBlank()) listOf("") else wrapText(line, maxTextWidth, 19f, Typeface.NORMAL)
            }.take(6)
            var baseline = top + 31f
            titleLines.forEach { line ->
                if (baseline <= bottom - 10f) {
                    drawText(line, textLeft, baseline, 25f, textColor, Typeface.BOLD)
                    baseline += 28f
                }
            }
            detailLines.forEach { line ->
                if (baseline <= bottom - 9f) {
                    if (line.isNotBlank()) {
                        drawText(line, textLeft, baseline, 19f, textColor, Typeface.NORMAL)
                    }
                    baseline += 24f
                }
            }
        }
        return bitmap
    }

    private fun createCourseFiles(): Pair<File, File>? {
        try {
            val directory = prepareShareCache()
            val pngFile = File(directory, "课程表.png")
            val csvFile = File(directory, "课程表.csv")
            val bitmap = createScheduleBitmap(
                activeScheduleTerm(), currentWeek, scheduleMode, activeScheduleCourses(), viewingPublicSchedule
            )
            try {
                FileOutputStream(pngFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
            writeCourseCsv(csvFile, activeScheduleCourses())
            return pngFile to csvFile
        } catch (error: Exception) {
            Toast.makeText(this, "导出失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    private fun saveSchedulePng() {
        if (scheduleExporting) return
        val term = activeScheduleTerm()
        val week = currentWeek
        val mode = scheduleMode
        val courses = activeScheduleCourses()
        if (courses.isEmpty()) {
            Toast.makeText(this, "课表尚未准备好", Toast.LENGTH_SHORT).show()
            return
        }
        scheduleExporting = true
        Toast.makeText(this, "正在保存课表图片…", Toast.LENGTH_SHORT).show()
        networkExecutor.execute {
            var bitmap: Bitmap? = null
            try {
                bitmap = createScheduleBitmap(term, week, mode, courses, viewingPublicSchedule)
                val displayName = if (viewingPublicSchedule) {
                    "专业课表-$term-$publicScheduleClassName.png"
                } else {
                    val weekName = if (week > 0) "第${week}周" else "学期未开始"
                    "课表-$term-$weekName.png"
                }
                saveScheduleBitmapToPictures(bitmap, displayName)
                runOnUiThread {
                    Toast.makeText(this, "课表图片已保存到 Pictures/WeSDAU", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "保存课表图片失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            } finally {
                bitmap?.recycle()
                scheduleExporting = false
            }
        }
    }

    private fun saveScheduleBitmapToPictures(bitmap: Bitmap, displayName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/WeSDAU")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建图片文件")
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "图片编码失败" }
                } ?: error("无法写入图片文件")
                contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            } catch (error: Exception) {
                contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            val directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.apply { mkdirs() }
                ?: error("无法访问图片目录")
            File(directory, displayName).outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "图片编码失败" }
            }
        }
    }

    private fun writeCourseCsv(file: File, courses: List<Course>) {
        val lines = mutableListOf(listOf("课程名称", "星期", "开始节数", "结束节数", "老师", "地点", "周数").joinToString(",") { csvEscape(it) })
        courses.forEach { course ->
            lines += listOf(
                course.name,
                (course.day + 1).toString(),
                (course.startSlot + 1).toString(),
                (course.startSlot + course.slotCount).toString(),
                course.teacher,
                course.room,
                course.weeks
            ).joinToString(",") { csvEscape(it) }
        }
        val content = lines.joinToString("\r\n") + "\r\n"
        FileOutputStream(file).use { output ->
            output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            output.write(content.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun shareSingleFile(file: File, mimeType: String, title: String) {
        val authority = "$packageName.fileprovider"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this@MainActivity, authority, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    private fun prepareShareCache(): File = File(cacheDir, "share").apply {
        mkdirs()
        listFiles()?.forEach { stale ->
            if (stale.isFile && System.currentTimeMillis() - stale.lastModified() > 60 * 60 * 1000L) {
                stale.delete()
            }
        }
    }

    private fun recolorCourses(courses: List<Course>): List<Course> {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val stored = runCatching { JSONObject(preferences.getString(KEY_COLOR_MAP, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val allNames = courses.map { it.name }.distinct().sorted()
        val actualWeek = weekForTerm(selectedTerm()).coerceAtLeast(1)
        val activeCourses = courses.filter { latestCourseWeek(it) >= actualWeek }
        val activeNames = activeCourses.map { it.name }.distinct()
        val activeNameSet = activeNames.toSet()
        val neighbours = buildCourseNeighbourGraph(activeCourses, actualWeek)
        val usedActiveIndices = mutableSetOf<Int>()
        val indexByName = mutableMapOf<String, Int>()

        // 先处理邻居最多的课程，能让密集区域优先获得差异最大的颜色。
        val allocationOrder = activeNames.sortedWith(
            compareByDescending<String> { neighbours[it]?.size ?: 0 }.thenBy { it }
        )
        val preferredOnly = activeNames.size <= COURSE_COLORS.size
        val candidateCount = if (preferredOnly) COURSE_COLORS.size else maxOf(COURSE_COLORS.size, activeNames.size + 8)
        allocationOrder.forEach { name ->
            val storedIndex = stored.optInt(name, -1)
            val candidateIndices = buildList {
                addAll(0 until candidateCount)
                if (storedIndex >= candidateCount) add(storedIndex)
            }.filterNot { it in usedActiveIndices }
            val assignedNeighbourIndices = neighbours[name].orEmpty().mapNotNull { indexByName[it] }
            val assignedAllIndices = indexByName.values.toList()
            val chosen = candidateIndices.maxByOrNull { candidate ->
                val color = courseColorAt(candidate)
                val neighbourDistance = assignedNeighbourIndices
                    .minOfOrNull { colorDistance(color, courseColorAt(it)) } ?: 420.0
                val globalDistance = assignedAllIndices
                    .minOfOrNull { colorDistance(color, courseColorAt(it)) } ?: 420.0
                val stabilityBonus = if (candidate == storedIndex) 42.0 else 0.0
                val generatedPenalty = if (candidate >= COURSE_COLORS.size) 55.0 else 0.0
                neighbourDistance * 3.2 + globalDistance * .35 + stabilityBonus - generatedPenalty - candidate * .015
            } ?: generateSequence(0) { it + 1 }.first { it !in usedActiveIndices }
            indexByName[name] = chosen
            usedActiveIndices += chosen
            stored.put(name, chosen)
        }

        // 已结束课程不再占用颜色名额，但保留自己的历史映射；因此它们可以与当前课程复用颜色。
        allNames.filterNot { it in activeNameSet }.forEach { name ->
            val historical = stored.optInt(name, -1).takeIf { it >= 0 } ?: 0
            indexByName[name] = historical
            stored.put(name, historical)
        }
        val activeMap = JSONObject()
        allNames.forEach { name -> activeMap.put(name, indexByName[name] ?: 0) }
        preferences.edit().putString(KEY_COLOR_MAP, activeMap.toString()).apply()
        return courses.map { it.copy(background = courseColorAt(indexByName[it.name] ?: 0)) }
    }

    private fun latestCourseWeek(course: Course): Int {
        val normalized = course.weeks.replace("周", "").replace("—", "-").replace("至", "-")
        val ranges = Regex("(\\d+)(?:\\s*-\\s*(\\d+))?").findAll(normalized).toList()
        if (ranges.isEmpty()) return 20
        return ranges.maxOfOrNull { match ->
            match.groupValues[2].toIntOrNull() ?: match.groupValues[1].toIntOrNull() ?: 20
        }?.coerceIn(1, 20) ?: 20
    }

    private fun buildCourseNeighbourGraph(courses: List<Course>, fromWeek: Int): Map<String, Set<String>> {
        val graph = courses.map { it.name }.distinct().associateWith { mutableSetOf<String>() }
        for (firstIndex in courses.indices) {
            val first = courses[firstIndex]
            for (secondIndex in firstIndex + 1 until courses.size) {
                val second = courses[secondIndex]
                if (first.name == second.name || !courseBlocksAreNear(first, second)) continue
                val coexist = (fromWeek..20).any { week ->
                    courseVisibleInWeek(first, week) && courseVisibleInWeek(second, week)
                }
                if (!coexist) continue
                graph[first.name]?.add(second.name)
                graph[second.name]?.add(first.name)
            }
        }
        return graph.mapValues { it.value.toSet() }
    }

    private fun courseBlocksAreNear(first: Course, second: Course): Boolean {
        val dayGap = kotlin.math.abs(first.day - second.day)
        if (dayGap > 1) return false
        val firstEnd = first.startSlot + first.slotCount
        val secondEnd = second.startSlot + second.slotCount
        val slotGap = when {
            firstEnd < second.startSlot -> second.startSlot - firstEnd
            secondEnd < first.startSlot -> first.startSlot - secondEnd
            else -> 0
        }
        // 同一天上下相邻、或相邻两天处在同一时间带的卡片，都属于视觉邻居。
        return if (dayGap == 0) slotGap <= 1 else slotGap == 0
    }

    /** Compuphase 加权 RGB 距离，比直接比较色相更符合人眼对明暗与红蓝差异的感知。 */
    private fun colorDistance(first: Int, second: Int): Double {
        val redMean = (Color.red(first) + Color.red(second)) / 2.0
        val red = Color.red(first) - Color.red(second)
        val green = Color.green(first) - Color.green(second)
        val blue = Color.blue(first) - Color.blue(second)
        val weightRed = 2.0 + redMean / 256.0
        val weightBlue = 2.0 + (255.0 - redMean) / 256.0
        return kotlin.math.sqrt(weightRed * red * red + 4.0 * green * green + weightBlue * blue * blue)
    }

    private fun courseColorAt(index: Int): Int {
        if (index < COURSE_COLORS.size) return COURSE_COLORS[index]
        return Color.HSVToColor(floatArrayOf((index * 137.508f) % 360f, 0.48f, 0.90f))
    }

    private fun shareWeekPng() {
        saveSchedulePng()
        hideSharePicker()
    }

    private fun shareCsv() {
        createCourseFiles()?.second?.let { shareSingleFile(it, "text/csv", "分享课程 CSV") }
        hideSharePicker()
    }

    private fun shareApp() {
        try {
            val directory = prepareShareCache()
            val apk = File(directory, "WeSDAU课程表.apk")
            File(applicationInfo.sourceDir).copyTo(apk, overwrite = true)
            shareSingleFile(apk, "application/vnd.android.package-archive", "分享 WeSDAU课程表")
        } catch (error: Exception) {
            Toast.makeText(this, "分享 APP 失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
        }
        hideSharePicker()
    }

    private fun showSharePicker() {
        if (shareOverlay != null) return
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(145, 12, 18, 30))
            isClickable = true
            setOnClickListener { hideSharePicker() }
        }
        val card = surfaceCard(dp(24f).toFloat()).apply {
            setCardBackgroundColor(PAGE_BACKGROUND)
            setOnClickListener { }
        }
        val body = verticalLayout().apply { setPadding(dp(20), dp(18), dp(20), dp(18)) }
        body.addView(text("分享", 20f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(12)))
        body.addView(shareRow(
            if (viewingPublicSchedule) "导出本专业课表为PNG" else "导出本周课表为PNG",
            if (viewingPublicSchedule) "包含课程周数" else "",
            R.drawable.ic_share_image
        ) { shareWeekPng() }, spacedParams(dp(8)))
        body.addView(shareRow("分享CSV文件", "可直接导入WakeUp课程表", R.drawable.ic_share_spreadsheet) { shareCsv() }, spacedParams(dp(8)))
        body.addView(shareRow("分享 APP", "WeSDAU课程表安装包", R.drawable.ic_share_app) { shareApp() }, matchWrapParams())
        card.addView(body)
        val width = minOf(dp(330f), resources.displayMetrics.widthPixels - dp(36f))
        overlay.addView(card, FrameLayout.LayoutParams(width, -2, Gravity.CENTER))
        pageHost.addView(overlay, matchParentParams())
        shareOverlay = overlay
        overlay.alpha = 0f; card.scaleX = .92f; card.scaleY = .92f
        overlay.animate().alpha(1f).setDuration(160).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    private fun shareRow(title: String, subtitle: String, icon: Int, action: () -> Unit): View {
        val row = MaterialCardView(this).apply {
            radius = dp(14f).toFloat(); cardElevation = 0f; setCardBackgroundColor(SURFACE); setOnClickListener { action() }
        }
        val content = horizontalLayout().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)) }
        content.addView(ImageButton(this).apply { setImageResource(icon); setBackgroundColor(Color.TRANSPARENT); isClickable = false }, LinearLayout.LayoutParams(dp(40), dp(40)))
        val labels = verticalLayout().apply { setPadding(dp(12), 0, 0, 0) }
        labels.addView(text(title, 15f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(3)))
        if (subtitle.isNotEmpty()) labels.addView(text(subtitle, 12f, TEXT_SECONDARY, Typeface.NORMAL), matchWrapParams())
        content.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(content)
        return row
    }

    private fun hideSharePicker() {
        val overlay = shareOverlay ?: return
        overlay.animate().alpha(0f).setDuration(140).withEndAction {
            pageHost.removeView(overlay)
            shareOverlay = null
        }.start()
    }

    private fun nextCourseForNow(): Course? {
        val courses = loadCourseCache()
        val actualWeek = weekForTerm(selectedTerm())
        if (actualWeek <= 0) return firstCourseForOpening(courses)
        val now = Calendar.getInstance()
        val today = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val starts = currentStartMinutes()
        val next = courses.filter { courseVisibleInWeek(it, actualWeek) }
            .map { course ->
                val dayDelta = (course.day - today + 7) % 7
                Triple(dayDelta, starts[course.startSlot], course)
            }
            .filter { it.first > 0 || it.second > minute }
            .minWithOrNull(compareBy<Triple<Int, Int, Course>> { it.first }.thenBy { it.second })
            ?.third
        return next ?: firstCourseForOpening(courses)
    }

    private fun firstCourseForOpening(courses: List<Course>): Course? {
        return courses.filter { courseVisibleInWeek(it, 1) }
            .minWithOrNull(compareBy<Course> { it.day }.thenBy { it.startSlot })
            ?: courses.minWithOrNull(compareBy<Course> { it.day }.thenBy { it.startSlot })
    }

    private fun courseVisibleInWeek(course: Course, week: Int): Boolean {
        // 第 0 周专门表示“学期尚未开始”。即使课程没有填写周次范围，
        // 也不能在开学日期之前绘制或响应点击。
        if (week <= 0) return false
        val normalized = course.weeks.replace("周", "").replace("—", "-").replace("至", "-")
        val ranges = Regex("(\\d+)(?:\\s*-\\s*(\\d+))?").findAll(normalized).toList()
        if (ranges.isEmpty()) return true
        return ranges.any { match ->
            val start = match.groupValues[1].toIntOrNull() ?: return@any false
            val end = match.groupValues[2].toIntOrNull() ?: start
            week in start.coerceAtLeast(1)..end
        }
    }

    private fun changeWeek(delta: Int, swipeDirection: Int = 0) {
        val nextWeek = (currentWeek + delta).coerceIn(0, 20)
        if (nextWeek == currentWeek) return
        currentWeek = nextWeek
        scheduleWeek?.text = formatWeekLabel(currentWeek)
        val grid = scheduleGrid ?: return
        if (swipeDirection == 0) {
            grid.setWeekIndex(currentWeek)
            return
        }
        val distance = dp(72f).toFloat() * swipeDirection
        grid.animate().translationX(distance).alpha(0f).setDuration(150).withEndAction {
            grid.setWeekIndex(currentWeek)
            grid.translationX = -distance
            grid.animate().translationX(0f).alpha(1f).setDuration(180).start()
        }.start()
    }

    private fun jumpToCurrentWeek() {
        val actualWeek = weekForTerm(selectedTerm())
        if (actualWeek == currentWeek) return
        val direction = if (actualWeek > currentWeek) -1 else 1
        currentWeek = actualWeek
        scheduleWeek?.text = formatWeekLabel(currentWeek)
        val grid = scheduleGrid ?: return
        val distance = dp(72f).toFloat() * direction
        grid.animate().translationX(distance).alpha(0f).setDuration(140).withEndAction {
            grid.setWeekIndex(currentWeek)
            grid.translationX = -distance
            grid.animate().translationX(0f).alpha(1f).setDuration(190).start()
        }.start()
    }

    private fun currentStartMinutes() = if (scheduleMode == ScheduleMode.SPRING) {
        intArrayOf(480, 535, 600, 655, 840, 895, 960, 1015, 1140, 1195)
    } else {
        intArrayOf(480, 535, 600, 655, 870, 925, 990, 1045, 1170, 1225)
    }

    private fun showScheduleModePicker() {
        if (modeOverlay != null) return
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(145, 12, 18, 30))
            isClickable = true
            setOnClickListener { hideScheduleModePicker() }
        }
        val card = surfaceCard(dp(24f).toFloat()).apply {
            setCardBackgroundColor(SCHEDULE_BACKGROUND)
            setOnClickListener { }
        }
        val body = verticalLayout().apply { setPadding(dp(22), dp(20), dp(22), dp(20)) }
        body.addView(text("选择作息时间", 20f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(15)))
        body.addView(scheduleModeRow("春秋作息", "秋季开学到次年“五一”假期", ScheduleMode.SPRING), spacedParams(dp(10)))
        body.addView(scheduleModeRow("夏季作息", "“五一”放假结束后至暑假", ScheduleMode.SUMMER), matchWrapParams())
        card.addView(body)
        val width = minOf(dp(330f), resources.displayMetrics.widthPixels - dp(36f))
        overlay.addView(card, FrameLayout.LayoutParams(width, -2, Gravity.CENTER))
        pageHost.addView(overlay, matchParentParams())
        modeOverlay = overlay
        overlay.alpha = 0f; card.scaleX = .92f; card.scaleY = .92f
        overlay.animate().alpha(1f).setDuration(160).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    private fun scheduleModeRow(title: String, subtitle: String, mode: ScheduleMode): View {
        val row = MaterialCardView(this).apply {
            radius = dp(16f).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(if (scheduleMode == mode) PRIMARY_CONTAINER else SURFACE)
            setOnClickListener {
                scheduleMode = mode
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(KEY_SCHEDULE_MODE, mode.name)
                    .apply()
                scheduleGrid?.setScheduleMode(mode)
                CourseWidgetProvider.updateAll(this@MainActivity)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    scheduleSystemCourseReminder()
                }
                hideScheduleModePicker()
            }
        }
        val content = verticalLayout().apply { setPadding(dp(16), dp(12), dp(16), dp(12)) }
        content.addView(text(title, 16f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(4)))
        content.addView(text(subtitle, 12f, TEXT_SECONDARY, Typeface.NORMAL), matchWrapParams())
        row.addView(content)
        return row
    }

    private fun hideScheduleModePicker() {
        val overlay = modeOverlay ?: return
        overlay.animate().alpha(0f).setDuration(140).withEndAction {
            pageHost.removeView(overlay)
            modeOverlay = null
        }.start()
    }

    private fun showSemesterPicker() {
        if (semesterOverlay != null) return
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(145, 12, 18, 30))
            isClickable = true
            setOnClickListener { hideSemesterPicker() }
        }
        val card = surfaceCard(dp(24f).toFloat()).apply {
            setCardBackgroundColor(PAGE_BACKGROUND)
            setOnClickListener { }
        }
        val body = verticalLayout().apply { setPadding(dp(20), dp(18), dp(20), dp(18)) }
        body.addView(text("选择学期", 20f, TEXT_PRIMARY, Typeface.BOLD), spacedParams(dp(12)))
        semesterOptions().forEach { semester ->
            val row = MaterialCardView(this).apply {
                radius = dp(13f).toFloat()
                cardElevation = 0f
                strokeWidth = 0
                setCardBackgroundColor(if (semester == semesterInput.text?.toString()) PRIMARY_CONTAINER else SURFACE)
                setOnClickListener {
                    semesterInput.setText(semester, false)
                    hideSemesterPicker()
                    if (loginMode == LoginMode.PUBLIC) {
                        publicCollegeSelection = ""
                        publicGradeSelection = ""
                        publicMajorSelection = ""
                        publicClassSelection = ""
                        startPublicScheduleSyncIfNeeded(semester)
                        swapPage(buildLoginPage(), false, false)
                    }
                }
            }
            row.addView(text(semester, 15f, TEXT_PRIMARY, Typeface.NORMAL).apply {
                setPadding(dp(14), dp(10), dp(14), dp(10))
            })
            body.addView(row, spacedParams(dp(7)))
        }
        card.addView(body)
        val width = minOf(dp(330f), resources.displayMetrics.widthPixels - dp(36f))
        overlay.addView(card, FrameLayout.LayoutParams(width, -2, Gravity.CENTER))
        pageHost.addView(overlay, matchParentParams())
        semesterOverlay = overlay
        overlay.alpha = 0f; card.scaleX = .92f; card.scaleY = .92f
        overlay.animate().alpha(1f).setDuration(160).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    private fun hideSemesterPicker() {
        val overlay = semesterOverlay ?: return
        overlay.animate().alpha(0f).setDuration(140).withEndAction {
            pageHost.removeView(overlay)
            semesterOverlay = null
        }.start()
    }

    private fun triggerTestNotification() {
        CourseNotification.createChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingTestNotification = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
            return
        }
        val course = nextCourseForNow() ?: loadCourseCache().firstOrNull() ?: return
        CourseNotification.show(this, course.name, course.room, courseTimeLabel(course))
    }

    private fun updatePushButton() {
        pushButton?.setImageResource(if (pushEnabled) R.drawable.ic_push_on else R.drawable.ic_push_off)
        pushButton?.contentDescription = if (pushEnabled) "关闭课程推送" else "开启课程推送"
    }

    private fun togglePushNotifications() {
        if (pushEnabled) {
            pushEnabled = false
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_PUSH_ENABLED, false).apply()
            cancelSystemCourseReminder()
            updatePushButton()
            Toast.makeText(this, "课程提醒已关闭", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingPushEnable = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
            return
        }
        enablePushNotifications()
    }

    private fun enablePushNotifications() {
        pushEnabled = true
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_PUSH_ENABLED, true).apply()
        updatePushButton()
        Toast.makeText(this, "课程提醒已开启", Toast.LENGTH_SHORT).show()
        requestBatteryOptimizationExemption()
        schedulePushNotifications()
        nextCourseForNow()?.let { course -> CourseNotification.show(this, course.name, course.room, courseTimeLabel(course)) }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (power.isIgnoringBatteryOptimizations(packageName)) {
            preferences.edit().putBoolean(KEY_BATTERY_PROMPTED, true).apply()
            return
        }
        if (preferences.getBoolean(KEY_BATTERY_PROMPTED, false)) return
        preferences.edit().putBoolean(KEY_BATTERY_PROMPTED, true).apply()
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (_: ActivityNotFoundException) {
            // Some OEMs do not expose the standard battery optimization screen.
        }
    }

    private fun prepareSystemCourseReminder() {
        CourseNotification.createChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
            return
        }
        scheduleSystemCourseReminder()
    }

    private fun scheduleSystemCourseReminder() {
        schedulePushNotifications()
    }

    private fun schedulePushNotifications() {
        if (!pushEnabled) return
        cancelPushAlarmsOnly()
        val courses = loadCourseCache().filter { courseVisibleInWeek(it, currentWeek) }
        if (courses.isEmpty()) return
        val now = Calendar.getInstance()
        val today = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val todayCourses = courses.filter { it.day == today }
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        courses.groupBy { it.day }.forEach { (day, dayCourses) ->
            val dayDelta = (day - today + 7) % 7
            dayCourses.sortedBy { it.startSlot }.forEachIndexed { index, course ->
                val start = courseStartTime(course, dayDelta)
                var trigger = start.timeInMillis - if (index == 0) 30 * 60_000L else 20 * 60_000L
                if (dayDelta == 1 && todayCourses.isEmpty() && index == 0) {
                    trigger = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1); set(Calendar.HOUR_OF_DAY, 22); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
                }
                if (trigger <= System.currentTimeMillis() + 5_000L) return@forEachIndexed
                val intent = Intent(this, CourseReminderReceiver::class.java).apply {
                    putExtra(CourseReminderReceiver.EXTRA_NAME, course.name)
                    putExtra(CourseReminderReceiver.EXTRA_ROOM, course.room)
                    putExtra(CourseReminderReceiver.EXTRA_TIME, courseTimeLabel(course))
                }
                val requestCode = REMINDER_REQUEST_CODE + day * 20 + course.startSlot
                val pending = android.app.PendingIntent.getBroadcast(this, requestCode, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        }
    }

    private fun courseStartTime(course: Course, dayDelta: Int): Calendar {
        val starts = currentStartMinutes()
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, dayDelta)
            set(Calendar.HOUR_OF_DAY, starts[course.startSlot] / 60)
            set(Calendar.MINUTE, starts[course.startSlot] % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
    }

    private fun courseTimeLabel(course: Course): String {
        val ranges = if (scheduleMode == ScheduleMode.SPRING) springTimeRanges() else summerTimeRanges()
        return "${ranges[course.startSlot].first}-${ranges[course.startSlot + course.slotCount - 1].second}"
    }

    private fun springTimeRanges() = arrayOf(
        "8:00" to "8:45", "8:55" to "9:40", "10:00" to "10:45", "10:55" to "11:40", "14:00" to "14:45",
        "14:55" to "15:40", "16:00" to "16:45", "16:55" to "17:40", "19:00" to "19:45", "19:55" to "20:40"
    )

    private fun summerTimeRanges() = arrayOf(
        "8:00" to "8:45", "8:55" to "9:40", "10:00" to "10:45", "10:55" to "11:40", "14:30" to "15:15",
        "15:25" to "16:10", "16:30" to "17:15", "17:25" to "18:10", "19:30" to "20:15", "20:25" to "21:10"
    )

    private fun courseReminderTime(course: Course): Long {
        val starts = currentStartMinutes()
        val now = Calendar.getInstance()
        val today = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val dayDelta = (course.day - today + 7) % 7
        val target = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, dayDelta)
            set(Calendar.HOUR_OF_DAY, starts[course.startSlot] / 60)
            set(Calendar.MINUTE, starts[course.startSlot] % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return target.timeInMillis - 10 * 60 * 1000L
    }

    private fun cancelSystemCourseReminder() {
        if (!::pageHost.isInitialized) return
        cancelPushAlarmsOnly()
        CourseNotification.cancel(this)
    }

    private fun cancelPushAlarmsOnly() {
        val intent = Intent(this, CourseReminderReceiver::class.java)
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (requestCode in REMINDER_REQUEST_CODE until REMINDER_REQUEST_CODE + 200) {
            val pending = android.app.PendingIntent.getBroadcast(this, requestCode, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            alarm.cancel(pending)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (pendingPushEnable) {
                pendingPushEnable = false
                enablePushNotifications()
                return
            }
            if (pendingTestNotification) {
                pendingTestNotification = false
                val course = nextCourseForNow() ?: loadCourseCache().firstOrNull()
                if (course != null) CourseNotification.show(this, course.name, course.room, courseTimeLabel(course))
            } else {
                scheduleSystemCourseReminder()
            }
        }
    }

    private inner class ScheduleScrollView(context: Context) : ScrollView(context) {
        private var downX = 0f
        private var downY = 0f
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (kotlin.math.abs(dx) > touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) return false
                }
            }
            return super.onInterceptTouchEvent(event)
        }
    }

    private fun formatWeekDate(week: Int): String {
        val date = termStartDate(selectedTerm()).apply { add(Calendar.DAY_OF_MONTH, (week - 1) * 7 + 1) }
        return SimpleDateFormat("yyyy/M/d", Locale.CHINA).format(date.time)
    }

    private fun todayLabel(): String = SimpleDateFormat("yyyy/M/d", Locale.CHINA).format(Calendar.getInstance().time)

    private fun daysUntilTermStart(): Int {
        val start = termStartDate(selectedTerm()).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        return ((start.timeInMillis - today.timeInMillis) / 86_400_000L).toInt().coerceAtLeast(0)
    }

    private fun formatWeekLabel(week: Int): String = if (week > 0) "第 $week 周" else "学期未开始"

    private fun compactButton(label: String) = MaterialButton(this).apply {
        text = label
        textSize = 14f
        setTextColor(PRIMARY_DARK)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        isAllCaps = false
        cornerRadius = dp(14)
        insetTop = 0
        insetBottom = 0
        setPadding(dp(8), 0, dp(8), 0)
        backgroundTintList = ColorStateList.valueOf(PRIMARY_CONTAINER)
    }

    private fun sampleCourses() = listOf(
        Course(0, 0, 2, "高等数学 A", "5N201", "张风", Color.rgb(232, 126, 158), Color.WHITE),
        Course(0, 2, 2, "人工智能通识基础", "5S416", "高智勇", Color.rgb(231, 142, 168), Color.WHITE),
        Course(0, 4, 2, "大学英语", "北校12号楼310", "王老师", Color.rgb(230, 157, 126), Color.WHITE),
        Course(1, 0, 2, "大学化学", "E308", "陈田田", Color.rgb(181, 145, 226), Color.WHITE),
        Course(1, 2, 2, "程序设计基础", "N104", "陈老师", Color.rgb(103, 205, 191), Color.WHITE),
        Course(1, 6, 2, "体育", "S514", "孟猛", Color.rgb(109, 153, 222), Color.WHITE),
        Course(2, 0, 2, "计算机导论", "图信楼413", "李老师", Color.rgb(182, 147, 224), Color.WHITE),
        Course(2, 2, 2, "大学英语 B1", "图信楼大厅A区", "曹惠", Color.rgb(100, 158, 206), Color.WHITE),
        Course(2, 4, 2, "思想道德与法治", "北校文理大楼503", "赵老师", Color.rgb(91, 167, 205), Color.WHITE),
        Course(3, 0, 2, "习近平新时代中国特色社会主义思想概论", "19#408", "周老师", Color.rgb(235, 177, 101), Color.WHITE),
        Course(3, 2, 2, "大学物理", "南校区体育北足球场", "周老师", Color.rgb(236, 132, 107), Color.WHITE),
        Course(3, 6, 2, "新时代实践教育", "22#402", "李晨", Color.rgb(230, 128, 160), Color.WHITE),
        Course(4, 0, 2, "数据结构", "W205", "高老师", Color.rgb(100, 201, 187), Color.WHITE),
        Course(4, 2, 2, "高等数学 A1", "西北区体育N", "张风", Color.rgb(97, 202, 188), Color.WHITE),
        Course(4, 4, 2, "线性代数", "南校区实验楼C楼C241", "张风", Color.rgb(182, 147, 224), Color.WHITE)
    )

    private fun sampleScoreResult(term: String): RemoteScoreResult {
        val records = listOf(
            RemoteScore("BK000101", "高等数学 A", "5", "91", "-"),
            RemoteScore("BK000205", "人工智能通识基础", "2", "88", "-"),
            RemoteScore("BK000307", "大学英语", "2", "86", "-"),
            RemoteScore("BK090102", "程序设计基础", "3", "94", "-"),
            RemoteScore("BK090201", "数据结构", "3", "92", "-"),
            RemoteScore("BK000408", "大学物理", "3", "84", "-"),
            RemoteScore("BK000512", "思想道德与法治", "3", "90", "-")
        )
        return recalculateScoreResult(RemoteScoreResult(
            term = term,
            records = records,
            averageScore = "89.29",
            averageCreditGpa = "-",
            totalCredits = "21"
        ))
    }

    private fun sampleScoreDetail(record: RemoteScore): RemoteScoreDetail {
        val total = record.score.toDoubleOrNull() ?: 90.0
        val usual = (total + 4.0).coerceAtMost(100.0)
        val final = (total - usual * .4) / .6
        fun display(value: Double): String {
            val oneDecimal = String.format(Locale.US, "%.1f", value)
            return oneDecimal.removeSuffix(".0")
        }
        return RemoteScoreDetail(
            usualScore = display(usual),
            usualRatio = "40",
            finalScore = display(final),
            finalRatio = "60",
            totalScore = record.score
        )
    }

    private fun sampleExams() = listOf(
        RemoteExam(
            courseName = "高等数学A",
            examWeek = "10",
            examWeekday = "7",
            examSessions = "1-2",
            classroom = "E307B"
        ),
        RemoteExam(
            courseName = "C语言程序设计",
            examWeek = "17",
            examWeekday = "7",
            examSessions = "9-10",
            classroom = "N302"
        )
    )

    private fun hasLocalCourseCache(): Boolean {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).contains(KEY_COURSES)
    }

    private fun publicScheduleFile(term: String): File {
        val safeTerm = term.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(filesDir, "public_schedule_$safeTerm.json.gz")
    }

    private fun hasPublicScheduleCache(term: String): Boolean {
        val file = publicScheduleFile(term)
        return file.isFile && file.length() > 0L &&
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_PUBLIC_SCHEDULE_SYNCED_TERM, "") == term
    }

    private fun savePublicScheduleCache(term: String, records: List<RemotePublicCourse>) {
        val array = JSONArray()
        records.forEach { item ->
            array.put(JSONObject().apply {
                put("college", item.college)
                put("grade", item.grade)
                put("major", item.major)
                put("className", item.className)
                put("day", item.day)
                put("startSlot", item.startSlot)
                put("slotCount", item.slotCount)
                put("name", item.name)
                put("room", item.room)
                put("teacher", item.teacher)
                put("weeks", item.weeks)
                put("courseCode", item.courseCode)
            })
        }
        val target = publicScheduleFile(term)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        GZIPOutputStream(FileOutputStream(temporary)).bufferedWriter(Charsets.UTF_8).use { it.write(array.toString()) }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_PUBLIC_SCHEDULE_SYNCED_TERM, term)
            .apply()
        publicScheduleMemoryCache[term] = records
    }

    private fun loadPublicScheduleCache(term: String): List<RemotePublicCourse> {
        publicScheduleMemoryCache[term]?.let { return it }
        val file = publicScheduleFile(term)
        if (!file.isFile) return emptyList()
        return runCatching {
            val raw = GZIPInputStream(FileInputStream(file)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(RemotePublicCourse(
                        item.optString("college"), item.optString("grade"), item.optString("major"),
                        item.optString("className"), item.optInt("day"), item.optInt("startSlot"),
                        item.optInt("slotCount"), item.optString("name"), item.optString("room"),
                        item.optString("teacher").replace(Regex("\\s*\\[[^]]*\\]"), ""),
                        item.optString("weeks"), item.optString("courseCode")
                    ))
                }
            }.also { publicScheduleMemoryCache[term] = it }
        }.getOrDefault(emptyList())
    }

    private fun startPublicScheduleSyncIfNeeded(term: String) {
        if (term.isBlank()) return
        if (hasPublicScheduleCache(term)) {
            if (!publicScheduleMemoryCache.containsKey(term)) {
                networkExecutor.execute { loadPublicScheduleCache(term) }
            }
            return
        }
        if (publicSyncRunning) return
        publicSyncRunning = true
        networkExecutor.execute {
            try {
                val records = SdauCourseRepository().queryPublicCoursesFromMirror(term)
                savePublicScheduleCache(term, records)
                runOnUiThread {
                    publicSyncRunning = false
                    if (onLoginPage && loginMode == LoginMode.PUBLIC) {
                        swapPage(buildLoginPage(), false, false)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { publicSyncRunning = false }
            }
        }
    }

    private fun saveCourseCache(courses: List<Course>) {
        val array = JSONArray()
        courses.forEach { course ->
            array.put(JSONObject().apply {
                put("day", course.day)
                put("startSlot", course.startSlot)
                put("slotCount", course.slotCount)
                put("name", course.name)
                put("room", course.room)
                put("teacher", course.teacher)
                put("weeks", course.weeks)
                put("background", course.background)
                put("foreground", course.foreground)
            })
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_COURSES, array.toString())
            .apply()
        CourseWidgetProvider.updateAll(this)
    }

    private fun loadCourseCache(): List<Course> {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_COURSES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            recolorCourses(buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(Course(
                        item.getInt("day"), item.getInt("startSlot"), item.getInt("slotCount"),
                        item.getString("name"), item.getString("room"), item.getString("teacher"),
                        item.getInt("background"), item.getInt("foreground"), item.optString("weeks", "")
                    ))
                }
            }.ifEmpty { emptyList() })
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveExamCache(term: String, records: List<RemoteExam>) {
        val array = JSONArray()
        records.forEach { exam ->
            array.put(JSONObject().apply {
                put("courseName", exam.courseName)
                put("examWeek", exam.examWeek)
                put("examWeekday", exam.examWeekday)
                put("examSessions", exam.examSessions)
                put("classroom", exam.classroom)
            })
        }
        val payload = JSONObject().apply {
            put("term", term)
            put("records", array)
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_EXAMS, payload.toString())
            .apply()
    }

    private fun loadExamCache(): ExamCache? {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_EXAMS, null) ?: return null
        return runCatching {
            val payload = JSONObject(raw)
            val rows = payload.optJSONArray("records") ?: JSONArray()
            ExamCache(
                term = payload.optString("term"),
                records = buildList {
                    for (index in 0 until rows.length()) {
                        val row = rows.optJSONObject(index) ?: continue
                        add(RemoteExam(
                            courseName = row.optString("courseName"),
                            examWeek = row.optString("examWeek"),
                            examWeekday = row.optString("examWeekday"),
                            examSessions = row.optString("examSessions"),
                            classroom = row.optString("classroom", "-")
                        ))
                    }
                }
            )
        }.getOrNull()
    }

    private fun saveScoreCache(result: RemoteScoreResult) {
        val records = JSONArray()
        result.records.forEach { score ->
            records.put(JSONObject().apply {
                put("courseCode", score.courseCode)
                put("courseName", score.courseName)
                put("credit", score.credit)
                put("score", score.score)
                put("gpa", score.gpa)
                put("studentIdRaw", score.studentIdRaw)
                put("teachingTaskId", score.teachingTaskId)
                put("scoreRecordId", score.scoreRecordId)
            })
        }
        val payload = JSONObject().apply {
            put("term", result.term)
            put("statsScope", SCORE_STATS_SCOPE)
            put("averageScore", result.averageScore)
            put("averageCreditGpa", result.averageCreditGpa)
            put("totalCredits", result.totalCredits)
            put("records", records)
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_SCORES, payload.toString())
            .apply()
    }

    private fun saveStudentName(name: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_STUDENT_NAME, name.trim())
            .apply()
    }

    private fun loadScoreCache(): RemoteScoreResult? {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_SCORES, null) ?: return null
        return runCatching {
            val payload = JSONObject(raw)
            val records = payload.optJSONArray("records") ?: JSONArray()
            val result = RemoteScoreResult(
                term = payload.optString("term"),
                records = buildList {
                    for (index in 0 until records.length()) {
                        val row = records.optJSONObject(index) ?: continue
                        add(RemoteScore(
                            courseCode = row.optString("courseCode"),
                            courseName = row.optString("courseName"),
                            credit = row.optString("credit"),
                            score = row.optString("score", "-"),
                            gpa = row.optString("gpa", "-"),
                            studentIdRaw = row.optString("studentIdRaw"),
                            teachingTaskId = row.optString("teachingTaskId"),
                            scoreRecordId = row.optString("scoreRecordId")
                        ))
                    }
                },
                averageScore = payload.optString("averageScore", "-"),
                averageCreditGpa = payload.optString("averageCreditGpa", "-"),
                totalCredits = payload.optString("totalCredits", "-")
            )
            if (payload.optString("statsScope") == SCORE_STATS_SCOPE) {
                result.copy(records = applyCalculatedGradePoints(result.records))
            } else {
                result.copy(
                    records = applyCalculatedGradePoints(result.records),
                    averageScore = "-",
                    averageCreditGpa = "-"
                )
            }
        }.getOrNull()
    }

    private fun inputBox(hint: String) = TextInputLayout(this).apply {
        this.hint = hint
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        setBoxCornerRadii(dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat())
        boxStrokeWidth = dp(1)
        boxStrokeWidthFocused = dp(2)
        setBoxStrokeColorStateList(inputStrokeColors())
        defaultHintTextColor = ColorStateList.valueOf(TEXT_SECONDARY)
        hintTextColor = ColorStateList.valueOf(PRIMARY)
        setErrorTextColor(ColorStateList.valueOf(ERROR))
    }

    private fun selectorInputBox(hint: String) = inputBox(hint).apply {
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_NONE
        setBoxBackgroundColor(Color.TRANSPARENT)
        setBoxCornerRadii(0f, 0f, 0f, 0f)
        boxStrokeWidth = 0
        boxStrokeWidthFocused = 0
    }

    private fun selectorFieldBackground(enabled: Boolean): LayerDrawable {
        val layers = LayerDrawable(arrayOf(
            ColorDrawable(Color.TRANSPARENT),
            ColorDrawable(if (enabled) OUTLINE else Color.rgb(232, 234, 240))
        ))
        layers.setLayerGravity(1, Gravity.BOTTOM)
        layers.setLayerWidth(1, -1)
        layers.setLayerHeight(1, dp(1))
        return layers
    }

    private fun input(inputType: Int) = TextInputEditText(this).apply {
        setSingleLine(true); textSize = 16f; setTextColor(TEXT_PRIMARY); setHintTextColor(TEXT_SECONDARY)
        this.inputType = inputType; minHeight = dp(58); setPadding(dp(16), 0, dp(16), 0)
    }

    private fun inputStrokeColors() = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(PRIMARY, Color.rgb(232, 234, 240), OUTLINE)
    )

    private fun buttonColors() = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()), intArrayOf(PRIMARY_DARK, PRIMARY)
    )

    private fun surfaceCard(radius: Float) = MaterialCardView(this).apply {
        this.radius = radius; cardElevation = dp(2).toFloat(); setCardBackgroundColor(SURFACE)
        setStrokeColor(OUTLINE); strokeWidth = dp(1)
    }

    private fun text(value: String, size: Float, color: Int, style: Int) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setTypeface(Typeface.DEFAULT, style); includeFontPadding = false
    }

    private fun fixedAdaptiveText(
        value: String,
        maximumDp: Float,
        minimumDp: Float,
        color: Int,
        style: Int
    ) = TextView(this).apply {
        text = value
        setTextColor(color)
        setTypeface(Typeface.DEFAULT, style)
        includeFontPadding = false
        maxLines = 1
        setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(maximumDp).toFloat())
        setAutoSizeTextTypeUniformWithConfiguration(
            minimumDp.toInt(), maximumDp.toInt(), 1, TypedValue.COMPLEX_UNIT_DIP
        )
    }

    private fun hideKeyboard() {
        val focused = currentFocus ?: return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }

    @Deprecated("Use OnBackInvokedDispatcher on newer Android versions")
    override fun onBackPressed() {
        if (onLoginPage && loginMode == LoginMode.PUBLIC) {
            loginMode = LoginMode.PERSONAL
            swapPage(buildLoginPage(), false, true)
            return
        }
        super.onBackPressed()
    }

    private fun verticalLayout() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun horizontalLayout() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun matchParentParams() = FrameLayout.LayoutParams(-1, -1)
    private fun matchWrapParams() = LinearLayout.LayoutParams(-1, -2)
    private fun spacedParams(bottom: Int) = matchWrapParams().apply { bottomMargin = bottom }
    private fun dp(value: Number) = (value.toFloat() * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    /**
     * 使用 Catmull-Rom 曲线穿过用户给出的五个色点，再采样为 65 个颜色。
     * 相比直接做五段线性渐变，色彩变化的一阶导数连续，配合抖动绘制可显著减少色带。
     */
    private inner class SilkyGradientDrawable : Drawable() {
        private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        private val sampleCount = 65
        private val sampledColors = IntArray(sampleCount) { sampleIndex ->
            val position = sampleIndex / (sampleCount - 1f)
            sampleSmoothGradient(position)
        }
        private val sampledPositions = FloatArray(sampleCount) { it / (sampleCount - 1f) }

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            super.onBoundsChange(bounds)
            if (bounds.width() <= 0 || bounds.height() <= 0) return
            gradientPaint.shader = LinearGradient(
                bounds.left.toFloat(), bounds.top.toFloat(),
                bounds.right.toFloat(), bounds.bottom.toFloat(),
                sampledColors, sampledPositions, Shader.TileMode.CLAMP
            )
        }

        override fun draw(canvas: Canvas) {
            canvas.drawRect(bounds, gradientPaint)
        }

        override fun setAlpha(alpha: Int) {
            gradientPaint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            gradientPaint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE

        private fun sampleSmoothGradient(position: Float): Int {
            val lastSegment = GRADIENT_COLORS.size - 2
            val scaled = position.coerceIn(0f, 1f) * (GRADIENT_COLORS.size - 1)
            val segment = scaled.toInt().coerceIn(0, lastSegment)
            val t = (scaled - segment).coerceIn(0f, 1f)
            val p0 = GRADIENT_COLORS[(segment - 1).coerceAtLeast(0)]
            val p1 = GRADIENT_COLORS[segment]
            val p2 = GRADIENT_COLORS[segment + 1]
            val p3 = GRADIENT_COLORS[(segment + 2).coerceAtMost(GRADIENT_COLORS.lastIndex)]
            fun channel(shift: Int): Int {
                val a = (p0 shr shift) and 0xff
                val b = (p1 shr shift) and 0xff
                val c = (p2 shr shift) and 0xff
                val d = (p3 shr shift) and 0xff
                val t2 = t * t
                val t3 = t2 * t
                return (.5f * (2f * b + (-a + c) * t + (2f * a - 5f * b + 4f * c - d) * t2 + (-a + 3f * b - 3f * c + d) * t3))
                    .toInt().coerceIn(0, 255)
            }
            return Color.rgb(channel(16), channel(8), channel(0))
        }
    }

    private enum class EmptyAcademicState { EXAMS, GRADES, ROOMS, ROOM_QUERY }

    private inner class AcademicEmptyIllustration(
        context: Context,
        private val type: EmptyAcademicState
    ) : View(context) {
        private val illustrationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val illustrationRect = RectF()
        private val illustrationPath = Path()

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = when (type) {
                EmptyAcademicState.EXAMS -> "暂无考试安排"
                EmptyAcademicState.GRADES -> "暂无课程成绩"
                EmptyAcademicState.ROOMS -> "暂无空闲教室"
                EmptyAcademicState.ROOM_QUERY -> "等待查询空闲教室"
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.shader = null
            illustrationPaint.color = Color.argb(38, 255, 255, 255)
            canvas.drawCircle(cx, cy, dp(57f).toFloat(), illustrationPaint)
            illustrationPaint.color = Color.argb(18, 131, 140, 199)
            canvas.drawCircle(cx + dp(17f), cy - dp(8f), dp(42f).toFloat(), illustrationPaint)
            when (type) {
                EmptyAcademicState.EXAMS -> drawEmptyExam(canvas, cx, cy)
                EmptyAcademicState.GRADES -> drawEmptyGrades(canvas, cx, cy)
                EmptyAcademicState.ROOMS -> drawEmptyRooms(canvas, cx, cy, true)
                EmptyAcademicState.ROOM_QUERY -> drawWaitingRoomQuery(canvas, cx, cy)
            }
        }

        private fun drawEmptyExam(canvas: Canvas, cx: Float, cy: Float) {
            illustrationRect.set(
                cx - dp(39f), cy - dp(39f),
                cx + dp(31f), cy + dp(35f)
            )
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.argb(28, 55, 72, 125)
            canvas.drawRoundRect(
                illustrationRect.left + dp(2f), illustrationRect.top + dp(4f),
                illustrationRect.right + dp(2f), illustrationRect.bottom + dp(4f),
                dp(14f).toFloat(), dp(14f).toFloat(), illustrationPaint
            )
            illustrationPaint.color = Color.argb(205, 252, 253, 255)
            canvas.drawRoundRect(illustrationRect, dp(14f).toFloat(), dp(14f).toFloat(), illustrationPaint)
            illustrationPaint.style = Paint.Style.STROKE
            illustrationPaint.strokeWidth = dp(1.2f).toFloat()
            illustrationPaint.color = Color.argb(100, 126, 139, 179)
            canvas.drawRoundRect(illustrationRect, dp(14f).toFloat(), dp(14f).toFloat(), illustrationPaint)
            canvas.drawLine(
                illustrationRect.left + dp(8f), illustrationRect.top + dp(22f),
                illustrationRect.right - dp(8f), illustrationRect.top + dp(22f), illustrationPaint
            )
            illustrationPaint.strokeWidth = dp(3f).toFloat()
            illustrationPaint.color = Color.rgb(131, 140, 199)
            canvas.drawLine(cx - dp(20f), cy - dp(47f), cx - dp(20f), cy - dp(33f), illustrationPaint)
            canvas.drawLine(cx + dp(12f), cy - dp(47f), cx + dp(12f), cy - dp(33f), illustrationPaint)

            illustrationPaint.style = Paint.Style.FILL
            val dotColors = intArrayOf(
                Color.rgb(245, 108, 126), Color.rgb(131, 140, 199), Color.rgb(130, 173, 247),
                Color.rgb(131, 140, 199), Color.rgb(130, 173, 247), Color.rgb(245, 108, 126)
            )
            var colorIndex = 0
            for (row in 0..1) {
                for (column in 0..2) {
                    illustrationPaint.color = Color.argb(145, Color.red(dotColors[colorIndex]), Color.green(dotColors[colorIndex]), Color.blue(dotColors[colorIndex]))
                    canvas.drawCircle(
                        illustrationRect.left + dp(17f + column * 17f),
                        illustrationRect.top + dp(36f + row * 16f),
                        dp(3.2f).toFloat(), illustrationPaint
                    )
                    colorIndex++
                }
            }

            val clockX = cx + dp(32f)
            val clockY = cy + dp(29f)
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.rgb(248, 250, 255)
            canvas.drawCircle(clockX, clockY, dp(19f).toFloat(), illustrationPaint)
            illustrationPaint.style = Paint.Style.STROKE
            illustrationPaint.strokeWidth = dp(2f).toFloat()
            illustrationPaint.color = Color.rgb(131, 140, 199)
            canvas.drawCircle(clockX, clockY, dp(16f).toFloat(), illustrationPaint)
            canvas.drawLine(clockX, clockY, clockX, clockY - dp(8f), illustrationPaint)
            canvas.drawLine(clockX, clockY, clockX + dp(7f), clockY + dp(4f), illustrationPaint)
        }

        private fun drawEmptyGrades(canvas: Canvas, cx: Float, cy: Float) {
            val save = canvas.save()
            canvas.rotate(-4f, cx, cy)
            illustrationRect.set(cx - dp(37f), cy - dp(43f), cx + dp(35f), cy + dp(39f))
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.argb(27, 55, 72, 125)
            canvas.drawRoundRect(
                illustrationRect.left + dp(3f), illustrationRect.top + dp(4f),
                illustrationRect.right + dp(3f), illustrationRect.bottom + dp(4f),
                dp(13f).toFloat(), dp(13f).toFloat(), illustrationPaint
            )
            illustrationPaint.color = Color.argb(210, 252, 253, 255)
            canvas.drawRoundRect(illustrationRect, dp(13f).toFloat(), dp(13f).toFloat(), illustrationPaint)
            illustrationPaint.style = Paint.Style.STROKE
            illustrationPaint.strokeWidth = dp(1.2f).toFloat()
            illustrationPaint.color = Color.argb(95, 126, 139, 179)
            canvas.drawRoundRect(illustrationRect, dp(13f).toFloat(), dp(13f).toFloat(), illustrationPaint)

            illustrationPaint.strokeWidth = dp(3f).toFloat()
            illustrationPaint.color = Color.rgb(131, 140, 199)
            canvas.drawLine(cx - dp(23f), cy - dp(25f), cx + dp(18f), cy - dp(25f), illustrationPaint)
            illustrationPaint.strokeWidth = dp(2f).toFloat()
            illustrationPaint.color = Color.argb(100, 105, 113, 132)
            canvas.drawLine(cx - dp(23f), cy - dp(12f), cx + dp(8f), cy - dp(12f), illustrationPaint)
            canvas.drawLine(cx - dp(23f), cy, cx + dp(15f), cy, illustrationPaint)

            illustrationPaint.style = Paint.Style.FILL
            val baseY = cy + dp(25f)
            illustrationPaint.color = Color.rgb(245, 108, 126)
            canvas.drawRoundRect(cx - dp(21f), baseY - dp(12f), cx - dp(12f), baseY, dp(3f).toFloat(), dp(3f).toFloat(), illustrationPaint)
            illustrationPaint.color = Color.rgb(131, 140, 199)
            canvas.drawRoundRect(cx - dp(6f), baseY - dp(20f), cx + dp(3f), baseY, dp(3f).toFloat(), dp(3f).toFloat(), illustrationPaint)
            illustrationPaint.color = Color.rgb(130, 173, 247)
            canvas.drawRoundRect(cx + dp(9f), baseY - dp(28f), cx + dp(18f), baseY, dp(3f).toFloat(), dp(3f).toFloat(), illustrationPaint)
            canvas.restoreToCount(save)

            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.rgb(245, 108, 126)
            canvas.drawCircle(cx + dp(39f), cy - dp(27f), dp(8f).toFloat(), illustrationPaint)
            illustrationPaint.style = Paint.Style.STROKE
            illustrationPaint.strokeWidth = dp(1.8f).toFloat()
            illustrationPaint.color = Color.WHITE
            canvas.drawLine(cx + dp(35f), cy - dp(27f), cx + dp(38f), cy - dp(24f), illustrationPaint)
            canvas.drawLine(cx + dp(38f), cy - dp(24f), cx + dp(44f), cy - dp(31f), illustrationPaint)
        }

        private fun drawWaitingRoomQuery(canvas: Canvas, cx: Float, cy: Float) {
            val cardSave = canvas.save()
            canvas.rotate(-3f, cx, cy)
            illustrationRect.set(cx - dp(42f), cy - dp(38f), cx + dp(33f), cy + dp(38f))
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.argb(27, 55, 72, 125)
            canvas.drawRoundRect(
                illustrationRect.left + dp(3f), illustrationRect.top + dp(5f),
                illustrationRect.right + dp(3f), illustrationRect.bottom + dp(5f),
                dp(14f).toFloat(), dp(14f).toFloat(), illustrationPaint
            )
            illustrationPaint.color = Color.argb(218, 252, 253, 255)
            canvas.drawRoundRect(illustrationRect, dp(14f).toFloat(), dp(14f).toFloat(), illustrationPaint)
            illustrationPaint.style = Paint.Style.STROKE
            illustrationPaint.strokeWidth = dp(1.2f).toFloat()
            illustrationPaint.color = Color.argb(100, 126, 139, 179)
            canvas.drawRoundRect(illustrationRect, dp(14f).toFloat(), dp(14f).toFloat(), illustrationPaint)

            // 彩色查询条与小圆点提供和课程空状态一致的插画层次。
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.rgb(131, 140, 199)
            canvas.drawRoundRect(
                illustrationRect.left + dp(13f), illustrationRect.top + dp(14f),
                illustrationRect.right - dp(14f), illustrationRect.top + dp(25f),
                dp(5.5f).toFloat(), dp(5.5f).toFloat(), illustrationPaint
            )
            illustrationPaint.color = Color.argb(215, 255, 255, 255)
            canvas.drawRoundRect(
                illustrationRect.left + dp(21f), illustrationRect.top + dp(18f),
                illustrationRect.right - dp(23f), illustrationRect.top + dp(21f),
                dp(1.5f).toFloat(), dp(1.5f).toFloat(), illustrationPaint
            )

            val resultColors = intArrayOf(
                Color.rgb(130, 173, 247),
                Color.rgb(105, 205, 185),
                Color.rgb(245, 108, 126)
            )
            for (row in 0..2) {
                val rowY = illustrationRect.top + dp(37f + row * 13f)
                illustrationPaint.color = resultColors[row]
                canvas.drawCircle(illustrationRect.left + dp(18f), rowY, dp(3.5f).toFloat(), illustrationPaint)
                illustrationPaint.color = Color.argb(82, 105, 113, 132)
                canvas.drawRoundRect(
                    illustrationRect.left + dp(27f), rowY - dp(1.7f),
                    illustrationRect.right - dp(12f + row * 5f), rowY + dp(1.7f),
                    dp(1.7f).toFloat(), dp(1.7f).toFloat(), illustrationPaint
                )
            }
            canvas.restoreToCount(cardSave)

            // 右下角的大放大镜与卡片发生遮挡，形成和参考图杯子相同的前后关系。
            val lensX = cx + dp(27f)
            val lensY = cy + dp(20f)
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.argb(235, 248, 250, 255)
            canvas.drawCircle(lensX, lensY, dp(19f).toFloat(), illustrationPaint)
            illustrationPaint.style = Paint.Style.STROKE
            illustrationPaint.strokeWidth = dp(3.2f).toFloat()
            illustrationPaint.color = Color.rgb(131, 140, 199)
            canvas.drawCircle(lensX - dp(2f), lensY - dp(2f), dp(13f).toFloat(), illustrationPaint)
            canvas.drawLine(
                lensX + dp(8f), lensY + dp(8f),
                lensX + dp(19f), lensY + dp(19f), illustrationPaint
            )

            // 左上角两枚柔和彩叶延续图二的点缀方式。
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.rgb(105, 205, 185)
            val leafSave = canvas.save()
            canvas.rotate(-26f, cx - dp(34f), cy - dp(38f))
            canvas.drawOval(cx - dp(43f), cy - dp(43f), cx - dp(26f), cy - dp(33f), illustrationPaint)
            canvas.restoreToCount(leafSave)
            illustrationPaint.color = Color.rgb(130, 173, 247)
            val secondLeafSave = canvas.save()
            canvas.rotate(28f, cx - dp(20f), cy - dp(42f))
            canvas.drawOval(cx - dp(28f), cy - dp(47f), cx - dp(13f), cy - dp(37f), illustrationPaint)
            canvas.restoreToCount(secondLeafSave)
        }

        private fun drawEmptyRooms(canvas: Canvas, cx: Float, cy: Float, showNoRoomBadge: Boolean) {
            illustrationRect.set(cx - dp(39f), cy - dp(40f), cx + dp(32f), cy + dp(39f))
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.argb(27, 55, 72, 125)
            canvas.drawRoundRect(
                illustrationRect.left + dp(3f), illustrationRect.top + dp(4f),
                illustrationRect.right + dp(3f), illustrationRect.bottom + dp(4f),
                dp(13f).toFloat(), dp(13f).toFloat(), illustrationPaint
            )
            illustrationPaint.color = Color.argb(210, 252, 253, 255)
            canvas.drawRoundRect(illustrationRect, dp(13f).toFloat(), dp(13f).toFloat(), illustrationPaint)
            illustrationPaint.style = Paint.Style.STROKE
            illustrationPaint.strokeWidth = dp(1.2f).toFloat()
            illustrationPaint.color = Color.argb(95, 126, 139, 179)
            canvas.drawRoundRect(illustrationRect, dp(13f).toFloat(), dp(13f).toFloat(), illustrationPaint)

            // 顶部的教室门牌延续考试、成绩空状态的彩色信息条语言。
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.rgb(131, 140, 199)
            canvas.drawRoundRect(
                illustrationRect.left + dp(12f), illustrationRect.top + dp(10f),
                illustrationRect.right - dp(12f), illustrationRect.top + dp(23f),
                dp(4f).toFloat(), dp(4f).toFloat(), illustrationPaint
            )
            illustrationPaint.color = Color.argb(205, 255, 255, 255)
            canvas.drawRoundRect(
                illustrationRect.left + dp(19f), illustrationRect.top + dp(15f),
                illustrationRect.right - dp(19f), illustrationRect.top + dp(18f),
                dp(1.5f).toFloat(), dp(1.5f).toFloat(), illustrationPaint
            )

            val doorLeft = cx - dp(17f)
            val doorTop = cy - dp(8f)
            val doorRight = cx + dp(13f)
            val doorBottom = illustrationRect.bottom
            illustrationPaint.style = Paint.Style.STROKE
            illustrationPaint.strokeWidth = dp(2.2f).toFloat()
            illustrationPaint.color = Color.rgb(131, 140, 199)
            illustrationPath.reset()
            illustrationPath.moveTo(doorLeft, doorBottom)
            illustrationPath.lineTo(doorLeft, doorTop)
            illustrationPath.lineTo(doorRight, doorTop)
            illustrationPath.lineTo(doorRight, doorBottom)
            canvas.drawPath(illustrationPath, illustrationPaint)

            if (showNoRoomBadge) {
                illustrationPaint.style = Paint.Style.FILL
                illustrationPaint.color = Color.rgb(221, 225, 245)
                canvas.drawRoundRect(
                    doorLeft + dp(5f), doorTop + dp(5f),
                    doorRight - dp(5f), doorBottom,
                    dp(3f).toFloat(), dp(3f).toFloat(), illustrationPaint
                )
                illustrationPaint.color = Color.rgb(105, 205, 185)
                canvas.drawRoundRect(
                    doorLeft + dp(9f), doorTop + dp(10f),
                    doorRight - dp(9f), doorTop + dp(21f),
                    dp(2.5f).toFloat(), dp(2.5f).toFloat(), illustrationPaint
                )
                illustrationPaint.color = Color.rgb(245, 108, 126)
                canvas.drawCircle(doorRight - dp(9f), cy + dp(16f), dp(2.2f).toFloat(), illustrationPaint)
            } else {
                illustrationPath.reset()
                illustrationPath.moveTo(doorLeft + dp(5f), doorTop + dp(5f))
                illustrationPath.lineTo(doorRight + dp(9f), doorTop + dp(1f))
                illustrationPath.lineTo(doorRight + dp(9f), doorBottom - dp(1f))
                illustrationPath.lineTo(doorLeft + dp(5f), doorBottom - dp(6f))
                illustrationPath.close()
                illustrationPaint.style = Paint.Style.FILL
                illustrationPaint.color = Color.rgb(176, 193, 238)
                canvas.drawPath(illustrationPath, illustrationPaint)
                illustrationPaint.style = Paint.Style.STROKE
                illustrationPaint.strokeWidth = dp(1.7f).toFloat()
                illustrationPaint.color = Color.rgb(131, 140, 199)
                canvas.drawPath(illustrationPath, illustrationPaint)
                illustrationPaint.style = Paint.Style.FILL
                illustrationPaint.color = Color.rgb(245, 108, 126)
                canvas.drawCircle(doorRight + dp(2f), cy + dp(15f), dp(2.2f).toFloat(), illustrationPaint)
            }

            val badgeX = cx + dp(36f)
            val badgeY = cy + dp(25f)
            if (showNoRoomBadge) {
                illustrationPaint.style = Paint.Style.FILL
                illustrationPaint.color = Color.rgb(245, 108, 126)
                canvas.drawCircle(badgeX, badgeY, dp(14f).toFloat(), illustrationPaint)
                illustrationPaint.style = Paint.Style.STROKE
                illustrationPaint.strokeWidth = dp(2f).toFloat()
                illustrationPaint.color = Color.WHITE
                canvas.drawCircle(badgeX, badgeY, dp(7f).toFloat(), illustrationPaint)
                canvas.drawLine(badgeX, badgeY, badgeX, badgeY - dp(4f), illustrationPaint)
                canvas.drawLine(badgeX, badgeY, badgeX + dp(4f), badgeY + dp(2f), illustrationPaint)
            } else {
                illustrationPaint.style = Paint.Style.FILL
                illustrationPaint.color = Color.rgb(248, 250, 255)
                canvas.drawCircle(badgeX, badgeY, dp(15f).toFloat(), illustrationPaint)
                illustrationPaint.style = Paint.Style.STROKE
                illustrationPaint.strokeWidth = dp(2.2f).toFloat()
                illustrationPaint.color = Color.rgb(91, 108, 190)
                canvas.drawCircle(badgeX - dp(2f), badgeY - dp(2f), dp(8f).toFloat(), illustrationPaint)
                canvas.drawLine(
                    badgeX + dp(4f), badgeY + dp(4f),
                    badgeX + dp(10f), badgeY + dp(10f), illustrationPaint
                )
            }
        }
    }

    private enum class ScheduleMode { SPRING, SUMMER }

    private data class Course(val day: Int, val startSlot: Int, val slotCount: Int, val name: String, val room: String, val teacher: String, val background: Int, val foreground: Int, val weeks: String = "")

    private data class CoursePlacement(
        val course: Course,
        val column: Int,
        val columnCount: Int
    )

    private inner class LoginModeToggle(
        context: Context,
        initialMode: LoginMode,
        private val onModeSelected: (LoginMode, LoginModeToggle) -> Unit
    ) : FrameLayout(context) {
        private var selectedMode = initialMode
        private var animating = false
        private var selectionPosition = if (initialMode == LoginMode.PUBLIC) 1f else 0f
        private val trackBounds = RectF()
        private val selectionBounds = RectF()
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PUBLIC_TOGGLE_BACKGROUND
            style = Paint.Style.FILL
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PUBLIC_TOGGLE_BACKGROUND
            style = Paint.Style.FILL
            setShadowLayer(dp(5).toFloat(), 0f, 0f, Color.argb(115, 94, 181, 255))
        }
        private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PUBLIC_TOGGLE_OUTLINE
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
        }
        private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), Color.argb(72, 32, 39, 53))
        }
        private val personalLabel = text("个人课表", 15f, TEXT_PRIMARY, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            isClickable = true
            setOnClickListener { requestMode(LoginMode.PERSONAL) }
        }
        private val publicLabel = text("全校课表", 15f, TEXT_SECONDARY, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            isClickable = true
            setOnClickListener { requestMode(LoginMode.PUBLIC) }
        }

        init {
            personalLabel.text = "个人课表"
            personalLabel.textSize = 16f
            publicLabel.textSize = 16f
            setPadding(0, 0, 0, 0)
            setWillNotDraw(false)
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            setBackgroundColor(Color.TRANSPARENT)
            val labels = horizontalLayout().apply {
                gravity = Gravity.CENTER
                setBackgroundColor(Color.TRANSPARENT)
            }
            labels.addView(personalLabel, LinearLayout.LayoutParams(0, -1, 1f))
            labels.addView(publicLabel, LinearLayout.LayoutParams(0, -1, 1f))
            addView(labels, FrameLayout.LayoutParams(-1, -1))
            updateLabels()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val trackInset = dp(4).toFloat()
            if (width <= trackInset * 2f || height <= trackInset * 2f) return

            trackBounds.set(
                trackInset,
                trackInset,
                width.toFloat() - trackInset,
                height.toFloat() - trackInset
            )
            val trackRadius = trackBounds.height() / 2f
            canvas.drawRoundRect(trackBounds, trackRadius, trackRadius, glowPaint)
            canvas.drawRoundRect(trackBounds, trackRadius, trackRadius, trackPaint)
            canvas.drawRoundRect(trackBounds, trackRadius, trackRadius, outlinePaint)

            val selectionVerticalInset = dp(4).toFloat()
            val selectionHorizontalInset = dp(4).toFloat()
            val segmentWidth = trackBounds.width() / 2f
            val selectionWidth = segmentWidth - selectionHorizontalInset * 2f
            val selectionLeft = trackBounds.left + selectionHorizontalInset + segmentWidth * selectionPosition
            selectionBounds.set(
                selectionLeft,
                trackBounds.top + selectionVerticalInset,
                selectionLeft + selectionWidth,
                trackBounds.bottom - selectionVerticalInset
            )
            val selectionRadius = selectionBounds.height() / 2f
            canvas.drawRoundRect(selectionBounds, selectionRadius, selectionRadius, selectionPaint)
        }

        private fun requestMode(mode: LoginMode) {
            if (animating || mode == selectedMode) return
            selectedMode = mode
            animating = true
            updateLabels()
            val target = if (mode == LoginMode.PUBLIC) 1f else 0f
            // 表单过渡与胶囊滑动在同一帧启动，并共用相同时长与曲线。
            onModeSelected(mode, this)
            ValueAnimator.ofFloat(selectionPosition, target).apply {
                duration = 380L
                interpolator = PathInterpolator(.2f, .78f, .2f, 1f)
                addUpdateListener { animator ->
                    selectionPosition = animator.animatedValue as Float
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        selectionPosition = target
                        animating = false
                    }
                })
                start()
            }
        }

        private fun updateLabels() {
            val personalSelected = selectedMode == LoginMode.PERSONAL
            personalLabel.setTextColor(if (personalSelected) TEXT_PRIMARY else TEXT_SECONDARY)
            personalLabel.setTypeface(Typeface.DEFAULT, if (personalSelected) Typeface.BOLD else Typeface.NORMAL)
            publicLabel.setTextColor(if (personalSelected) TEXT_SECONDARY else TEXT_PRIMARY)
            publicLabel.setTypeface(Typeface.DEFAULT, if (personalSelected) Typeface.NORMAL else Typeface.BOLD)
            personalLabel.setBackgroundColor(Color.TRANSPARENT)
            publicLabel.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    /**
     * 轻量的液态玻璃底栏。它由半透明玻璃、双层高光和会流动的选中胶囊组成，
     * 不依赖额外图片资源，也不会改变上方课程表的七列网格模型。
     */
    private inner class LiquidGlassNavigationView(context: Context) : View(context) {
        private val labels = arrayOf("课程表", "考试安排", "成绩", "教室使用情况")
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bounds = RectF()
        private val indicatorBounds = RectF()
        private val trailBounds = RectF()
        private val iconBounds = RectF()
        private val shaderMatrix = Matrix()
        private var indicatorShader: LinearGradient? = null
        private var borderShader: LinearGradient? = null
        private var forwardRefractionShader: LinearGradient? = null
        private var reverseRefractionShader: LinearGradient? = null
        private val releaseInterpolator = PathInterpolator(.18f, .86f, .22f, 1f)
        private var selectedItem = 0
        private var committedItem = 0
        private var indicatorPosition = 0f
        private var lastDragItem = 0
        private var indicatorAnimator: ValueAnimator? = null
        private var velocityTracker: VelocityTracker? = null
        private var dragOriginPosition = 0f
        private var refractionAlpha = 0f
        private var indicatorScale = 1f
        private var selectionDispatchToken = 0
        private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
        private val maximumFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity
        var onItemSelected: ((Int, View) -> Unit)? = null

        init {
            setLayerType(LAYER_TYPE_HARDWARE, null)
            isClickable = true
            isFocusable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "校园功能底部导航"
        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)
            indicatorShader = LinearGradient(
                0f, 0f, 1f, 1f,
                intArrayOf(Color.argb(205, 255, 255, 255), Color.argb(185, 218, 224, 255), Color.argb(172, 238, 232, 255)),
                floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP
            )
            borderShader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(Color.argb(235, 255, 255, 255), Color.argb(90, 146, 158, 204), Color.argb(220, 255, 255, 255)),
                null, Shader.TileMode.CLAMP
            )
            val forwardColors = intArrayOf(
                Color.argb(18, 255, 255, 255), Color.argb(70, 255, 255, 255),
                Color.argb(28, 159, 181, 255), Color.argb(105, 255, 255, 255), Color.TRANSPARENT
            )
            forwardRefractionShader = LinearGradient(
                0f, 0f, 1f, 0f, forwardColors,
                floatArrayOf(0f, .2f, .48f, .78f, 1f), Shader.TileMode.CLAMP
            )
            reverseRefractionShader = LinearGradient(
                0f, 0f, 1f, 0f, forwardColors.reversedArray(),
                floatArrayOf(0f, .2f, .48f, .78f, 1f), Shader.TileMode.CLAMP
            )
        }

        fun selectItem(index: Int, animate: Boolean) {
            val target = index.coerceIn(labels.indices)
            selectedItem = target
            committedItem = target
            indicatorAnimator?.cancel()
            if (!animate) {
                indicatorPosition = target.toFloat()
                refractionAlpha = 0f
                indicatorScale = 1f
                invalidate()
                return
            }
            animateRelease(target, 0f)
        }

        private fun animateRelease(target: Int, velocityX: Float) {
            indicatorAnimator?.cancel()
            val targetPosition = target.toFloat()
            val startPosition = indicatorPosition
            val fastEdgeRelease = isFastEdgeRelease(target, velocityX)
            indicatorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                val travel = kotlin.math.abs(targetPosition - startPosition)
                duration = if (fastEdgeRelease) 260L else (280f + travel * 24f).toLong().coerceAtMost(325L)
                interpolator = releaseInterpolator
                addUpdateListener { animator ->
                    val fraction = animator.animatedFraction
                    val rawFraction = (animator.currentPlayTime.toFloat() / animator.duration.coerceAtLeast(1L))
                        .coerceIn(0f, 1f)
                    indicatorPosition = lerp(startPosition, targetPosition, fraction)
                    // 三个逐渐衰减的半波形成“膨胀—收缩—轻回弹”的阻尼弹簧。
                    val springAmplitude = if (fastEdgeRelease) .22f else .16f
                    val damping = kotlin.math.exp(-3.4f * rawFraction)
                    indicatorScale = 1f + springAmplitude * damping *
                        kotlin.math.sin((Math.PI * 3.0 * rawFraction)).toFloat()
                    refractionAlpha = ((1f - fraction) * 1.08f).coerceIn(0f, 1f)
                    postInvalidateOnAnimation()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        indicatorPosition = targetPosition
                        indicatorScale = 1f
                        refractionAlpha = 0f
                        invalidate()
                    }
                })
                start()
            }
        }

        private fun isFastEdgeRelease(target: Int, velocityX: Float): Boolean {
            val fast = kotlin.math.abs(velocityX) >= minimumFlingVelocity * 2.25f
            return fast && ((target == 0 && velocityX < 0f) ||
                (target == labels.lastIndex && velocityX > 0f))
        }

        private fun lerp(start: Float, end: Float, fraction: Float) = start + (end - start) * fraction

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = resolveSize(dp(216), widthMeasureSpec)
            val height = resolveSize(dp(54), heightMeasureSpec)
            setMeasuredDimension(width, height)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val edge = dp(2f).toFloat()
            bounds.set(edge, edge, width - edge, height - edge)
            val radius = dp(26f).toFloat()

            // 分层透明阴影保持玻璃悬浮感，并避免拖动时重复计算软件模糊阴影。
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = Color.argb(16, 50, 64, 112)
            canvas.drawRoundRect(
                bounds.left - dp(1f), bounds.top + dp(3f),
                bounds.right + dp(1f), bounds.bottom + dp(4f),
                radius + dp(1f), radius + dp(1f), paint
            )
            paint.color = Color.argb(18, 50, 64, 112)
            canvas.drawRoundRect(
                bounds.left, bounds.top + dp(2f), bounds.right, bounds.bottom + dp(2f),
                radius, radius, paint
            )
            paint.color = Color.argb(218, 249, 251, 255)
            canvas.drawRoundRect(bounds, radius, radius, paint)

            val itemWidth = (width - edge * 2f) / labels.size
            val indicatorLeft = edge + indicatorPosition * itemWidth + dp(4f)
            indicatorBounds.set(
                indicatorLeft, dp(5f).toFloat(),
                indicatorLeft + itemWidth - dp(8f), height.toFloat() - dp(5f)
            )
            val horizontalExpansion = indicatorBounds.width() * (indicatorScale - 1f) * .5f
            val verticalExpansion = indicatorBounds.height() * (indicatorScale - 1f) * .5f
            indicatorBounds.inset(-horizontalExpansion, -verticalExpansion)

            drawDragRefraction(canvas, edge, itemWidth, indicatorBounds)

            indicatorShader?.let { shader ->
                shaderMatrix.reset()
                shaderMatrix.setScale(indicatorBounds.width().coerceAtLeast(1f), indicatorBounds.height().coerceAtLeast(1f))
                shaderMatrix.postTranslate(indicatorBounds.left, indicatorBounds.top)
                shader.setLocalMatrix(shaderMatrix)
                paint.shader = shader
            }
            canvas.drawRoundRect(indicatorBounds, dp(22f).toFloat(), dp(22f).toFloat(), paint)
            paint.shader = null

            // 流体边缘的小光斑让胶囊在移动时更像凝聚的玻璃液滴。
            paint.color = Color.argb(105, 255, 255, 255)
            canvas.drawCircle(indicatorBounds.left + dp(11f), indicatorBounds.top + dp(8f), dp(6f).toFloat(), paint)
            paint.color = Color.argb(48, 116, 136, 235)
            canvas.drawCircle(indicatorBounds.right - dp(8f), indicatorBounds.bottom - dp(8f), dp(5f).toFloat(), paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f).toFloat()
            paint.shader = borderShader
            canvas.drawRoundRect(bounds, radius, radius, paint)
            paint.shader = null

            // 顶缘高光。
            paint.color = Color.argb(190, 255, 255, 255)
            paint.strokeWidth = dp(1.2f).toFloat()
            canvas.drawLine(dp(22f).toFloat(), dp(4f).toFloat(), width - dp(22f).toFloat(), dp(4f).toFloat(), paint)

            for (index in labels.indices) {
                val centerX = edge + itemWidth * (index + .5f)
                val active = index == selectedItem
                val currentInfluence = (1f - kotlin.math.abs(index - indicatorPosition)).coerceIn(0f, 1f)
                val trailStart = minOf(dragOriginPosition, indicatorPosition)
                val trailEnd = maxOf(dragOriginPosition, indicatorPosition)
                val indexPosition = index.toFloat()
                val passedInfluence = if (indexPosition >= trailStart && indexPosition <= trailEnd) .34f else 0f
                val refractionInfluence = maxOf(currentInfluence, passedInfluence) * refractionAlpha
                val lift = dp(2.2f) * refractionInfluence
                drawNavigationIcon(
                    canvas, index, centerX, height / 2f - lift,
                    active, refractionInfluence
                )
            }
        }

        private fun drawDragRefraction(canvas: Canvas, edge: Float, itemWidth: Float, indicator: RectF) {
            if (refractionAlpha <= .01f) return
            val originCenter = edge + itemWidth * (dragOriginPosition + .5f)
            val currentCenter = indicator.centerX()
            if (kotlin.math.abs(currentCenter - originCenter) < dp(2f)) return

            val left = minOf(originCenter, currentCenter) - itemWidth * .28f
            val right = maxOf(originCenter, currentCenter) + itemWidth * .28f
            trailBounds.set(left, dp(7f).toFloat(), right, height - dp(7f).toFloat())
            val directionFromLeft = currentCenter >= originCenter

            val checkpoint = canvas.save()
            canvas.clipRect(bounds)
            paint.style = Paint.Style.FILL
            val refractionShader = if (directionFromLeft) forwardRefractionShader else reverseRefractionShader
            refractionShader?.let { shader ->
                shaderMatrix.reset()
                shaderMatrix.setScale(trailBounds.width().coerceAtLeast(1f), 1f)
                shaderMatrix.postTranslate(trailBounds.left, 0f)
                shader.setLocalMatrix(shaderMatrix)
                paint.shader = shader
            }
            paint.alpha = (255 * refractionAlpha).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(trailBounds, dp(18f).toFloat(), dp(18f).toFloat(), paint)
            paint.alpha = 255
            paint.shader = null

            canvas.restoreToCount(checkpoint)
        }

        private fun drawNavigationIcon(
            canvas: Canvas,
            index: Int,
            cx: Float,
            cy: Float,
            active: Boolean,
            refraction: Float
        ) {
            if (refraction > .01f) {
                paint.style = Paint.Style.STROKE
                paint.shader = null
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.strokeWidth = dp(2.8f).toFloat()
                paint.color = Color.argb((70f * refraction).toInt(), 119, 151, 255)
                val checkpoint = canvas.save()
                canvas.translate(0f, dp(.85f) * refraction)
                drawNavigationIconShape(canvas, index, cx, cy)
                canvas.restoreToCount(checkpoint)
            }

            paint.style = Paint.Style.STROKE
            paint.shader = null
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = dp(if (active) 2.1f else 1.75f).toFloat()
            paint.color = if (active) PRIMARY_DARK else Color.rgb(105, 113, 132)
            drawNavigationIconShape(canvas, index, cx, cy)

            if (refraction > .01f) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb((125f * refraction).toInt(), 255, 255, 255)
                canvas.drawCircle(cx - dp(5.5f), cy - dp(6.5f), dp(1.15f) * refraction, paint)
            }
        }

        private fun drawNavigationIconShape(canvas: Canvas, index: Int, cx: Float, cy: Float) {
            val s = dp(8f).toFloat()
            when (index) {
                0 -> {
                    iconBounds.set(cx - s, cy - s * .72f, cx + s, cy + s * .78f)
                    canvas.drawRoundRect(iconBounds, dp(2.5f).toFloat(), dp(2.5f).toFloat(), paint)
                    canvas.drawLine(iconBounds.left, cy - s * .28f, iconBounds.right, cy - s * .28f, paint)
                    canvas.drawLine(cx - s * .48f, cy - s, cx - s * .48f, cy - s * .5f, paint)
                    canvas.drawLine(cx + s * .48f, cy - s, cx + s * .48f, cy - s * .5f, paint)
                }
                1 -> {
                    iconBounds.set(cx - s * .72f, cy - s, cx + s * .72f, cy + s)
                    canvas.drawRoundRect(iconBounds, dp(2f).toFloat(), dp(2f).toFloat(), paint)
                    canvas.drawLine(cx - s * .4f, cy - s * .48f, cx + s * .38f, cy - s * .48f, paint)
                    canvas.drawLine(cx - s * .4f, cy - s * .08f, cx + s * .2f, cy - s * .08f, paint)
                    canvas.drawCircle(cx + s * .34f, cy + s * .48f, s * .28f, paint)
                    canvas.drawLine(cx + s * .34f, cy + s * .48f, cx + s * .34f, cy + s * .31f, paint)
                    canvas.drawLine(cx + s * .34f, cy + s * .48f, cx + s * .47f, cy + s * .56f, paint)
                }
                2 -> {
                    canvas.drawLine(cx - s, cy + s * .9f, cx + s, cy + s * .9f, paint)
                    canvas.drawLine(cx - s * .65f, cy + s * .9f, cx - s * .65f, cy + s * .1f, paint)
                    canvas.drawLine(cx, cy + s * .9f, cx, cy - s * .45f, paint)
                    canvas.drawLine(cx + s * .65f, cy + s * .9f, cx + s * .65f, cy - s, paint)
                }
                3 -> {
                    canvas.drawCircle(cx - s * .18f, cy - s * .18f, s * .58f, paint)
                    canvas.drawLine(
                        cx + s * .23f, cy + s * .23f,
                        cx + s * .86f, cy + s * .86f,
                        paint
                    )
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            velocityTracker?.addMovement(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    indicatorAnimator?.cancel()
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                    selectionDispatchToken++
                    dragOriginPosition = committedItem.toFloat()
                    refractionAlpha = 1f
                    indicatorScale = 1f
                    val position = indicatorPositionForX(event.x)
                    indicatorPosition = position
                    selectedItem = nearestItem(position)
                    lastDragItem = selectedItem
                    parent?.requestDisallowInterceptTouchEvent(true)
                    postInvalidateOnAnimation()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val position = indicatorPositionForX(event.x)
                    indicatorPosition = position
                    selectedItem = nearestItem(position)
                    if (selectedItem != lastDragItem) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        lastDragItem = selectedItem
                    }
                    postInvalidateOnAnimation()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    velocityTracker?.computeCurrentVelocity(1000, maximumFlingVelocity.toFloat())
                    val velocityX = velocityTracker?.xVelocity ?: 0f
                    val releasePosition = indicatorPositionForX(event.x)
                    val itemWidth = ((width - dp(4f)).toFloat() / labels.size).coerceAtLeast(1f)
                    val projectedPosition = if (kotlin.math.abs(velocityX) >= minimumFlingVelocity * 2.25f) {
                        releasePosition + (velocityX / itemWidth).coerceIn(-1.8f, 1.8f) * .16f
                    } else {
                        releasePosition
                    }
                    val target = nearestItem(projectedPosition.coerceIn(0f, labels.lastIndex.toFloat()))
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    if (target != lastDragItem) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    selectedItem = target
                    committedItem = target
                    val fastEdgeRelease = isFastEdgeRelease(target, velocityX)
                    animateRelease(target, velocityX)
                    velocityTracker?.recycle()
                    velocityTracker = null
                    contentDescription = "已选择${labels[target]}"
                    val dispatchToken = ++selectionDispatchToken
                    if (fastEdgeRelease) {
                        postDelayed({
                            if (dispatchToken == selectionDispatchToken && committedItem == target) {
                                onItemSelected?.invoke(target, this)
                            }
                        }, 265L)
                    } else {
                        onItemSelected?.invoke(target, this)
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    selectedItem = committedItem
                    animateRelease(committedItem, 0f)
                    velocityTracker?.recycle()
                    velocityTracker = null
                    return true
                }
            }
            return true
        }

        private fun indicatorPositionForX(x: Float): Float {
            val edge = dp(2f).toFloat()
            val itemWidth = (width - edge * 2f) / labels.size
            return ((x - edge) / itemWidth - .5f).coerceIn(0f, labels.lastIndex.toFloat())
        }

        private fun nearestItem(position: Float) = (position + .5f).toInt().coerceIn(labels.indices)

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onDetachedFromWindow() {
            indicatorAnimator?.removeAllUpdateListeners()
            indicatorAnimator?.removeAllListeners()
            indicatorAnimator?.cancel()
            indicatorAnimator = null
            velocityTracker?.recycle()
            velocityTracker = null
            super.onDetachedFromWindow()
        }
    }

    private inner class ScheduleGridView(context: Context, private var courses: List<Course>) : View(context) {
        private val dayNames = arrayOf("一", "二", "三", "四", "五", "六", "日")
        private val springTimes = arrayOf(
            arrayOf("08:00", "08:50"), arrayOf("09:00", "09:50"), arrayOf("10:10", "11:00"),
            arrayOf("10:55", "11:40"), arrayOf("14:00", "14:45"), arrayOf("14:55", "15:40"),
            arrayOf("16:00", "16:45"), arrayOf("16:55", "17:40"), arrayOf("19:00", "19:45"),
            arrayOf("19:55", "20:40")
        )
        private val summerTimes = arrayOf(
            arrayOf("08:00", "08:45"), arrayOf("08:55", "09:40"), arrayOf("10:00", "10:45"),
            arrayOf("10:55", "11:40"), arrayOf("14:30", "15:15"), arrayOf("15:25", "16:10"),
            arrayOf("16:30", "17:15"), arrayOf("17:25", "18:10"), arrayOf("19:30", "20:15"),
            arrayOf("20:25", "21:10")
        )
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private var weekIndex = 1
        private var scheduleMode = ScheduleMode.SPRING
        private var timeColumnWidth = 0
        private var dayColumnWidth = 0
        private var headerHeight = 0
        private var slotHeight = 0
        private var desiredWidth = 0
        private var desiredHeight = 0
        private var downX = 0f
        private var downY = 0f
        private var dragOffset = 0f
        private var gestureAxis = 0 // 0 未确定，1 横向翻页，2 纵向手势
        private var pageAnimator: ValueAnimator? = null
        private var pageVelocityTracker: VelocityTracker? = null
        private var cachedCurrentPage: Bitmap? = null
        private var cachedAdjacentPage: Bitmap? = null
        private var cachedCurrentNode: RenderNode? = null
        private var cachedAdjacentNode: RenderNode? = null
        private var cachedCurrentWeek = -1
        private var cachedAdjacentWeek = -1
        private val pageBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val pageSettleInterpolator = PathInterpolator(.18f, .82f, .22f, 1f)
        private val viewConfiguration = ViewConfiguration.get(context)
        private val touchSlop = viewConfiguration.scaledTouchSlop
        private val pageMinimumFlingVelocity = viewConfiguration.scaledMinimumFlingVelocity
        private val pageMaximumFlingVelocity = viewConfiguration.scaledMaximumFlingVelocity

        init { setBackgroundColor(Color.TRANSPARENT); isFocusable = true; contentDescription = "开发测试周课程表，包含 9 门示例课程" }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            var width = MeasureSpec.getSize(widthMeasureSpec)
            val parentWidth = (parent as? View)?.measuredWidth ?: 0
            val parentContentWidth = (parentWidth - dp(16f)).coerceAtLeast(dp(280f))
            if (width <= 0 || parentContentWidth > width + dp(8f)) width = parentContentWidth
            desiredWidth = width
            timeColumnWidth = Math.max(dp(30f), Math.min(dp(44f), (width * .095f).toInt()))
            dayColumnWidth = ((width - timeColumnWidth) / 7).coerceAtLeast(1)
            headerHeight = dp(44f)
            val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
            if (availableHeight > 0) {
                desiredHeight = availableHeight
                slotHeight = ((availableHeight - headerHeight) / 11f).toInt().coerceAtLeast(dp(40f))
            } else {
                slotHeight = dp(48f)
                desiredHeight = headerHeight + slotHeight * 10
            }
            setMeasuredDimension(desiredWidth, desiredHeight)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (pageAnimator != null) return false
                    pageVelocityTracker?.recycle()
                    pageVelocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                    downX = event.x; downY = event.y
                    gestureAxis = 0
                    // 手指按下时先缓存当前周，把主要绘制成本移出连续拖动帧。
                    prepareSwipeBitmaps(-1)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    pageVelocityTracker?.addMovement(event)
                    val dx = event.x - downX; val dy = event.y - downY
                    if (gestureAxis == 0 && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                        gestureAxis = if (kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.1f) 1 else 2
                    }
                    if (gestureAxis == 2) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    } else if (gestureAxis == 1) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        val canMove = (dx < 0f && weekIndex < 20) || (dx > 0f && weekIndex > 0)
                        if (canMove) prepareSwipeBitmaps(if (dx < 0f) weekIndex + 1 else weekIndex - 1)
                        dragOffset = if (canMove) {
                            dx.coerceIn(-desiredWidth.toFloat(), desiredWidth.toFloat())
                        } else {
                            resistedEdgeOffset(dx)
                        }
                        postInvalidateOnAnimation()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    pageVelocityTracker?.addMovement(event)
                    pageVelocityTracker?.computeCurrentVelocity(1000, pageMaximumFlingVelocity.toFloat())
                    val velocityX = pageVelocityTracker?.xVelocity ?: 0f
                    val dx = event.x - downX; val dy = event.y - downY
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (gestureAxis == 1) {
                        val threshold = minOf(desiredWidth * .22f, dp(72f).toFloat())
                        val fastFling = kotlin.math.abs(velocityX) >= pageMinimumFlingVelocity * 1.35f
                        val projectedOffset = dragOffset + velocityX * .12f
                        val delta = if (fastFling) {
                            when {
                                velocityX < 0f && weekIndex < 20 -> 1
                                velocityX > 0f && weekIndex > 0 -> -1
                                else -> 0
                            }
                        } else {
                            when {
                                projectedOffset <= -threshold && weekIndex < 20 -> 1
                                projectedOffset >= threshold && weekIndex > 0 -> -1
                                else -> 0
                            }
                        }
                        settleDraggedWeek(delta, velocityX)
                    } else {
                        if (kotlin.math.abs(dx) <= touchSlop && kotlin.math.abs(dy) <= touchSlop) {
                            findCourseAt(event.x, event.y)?.let { showCourseDetails(it) }
                        }
                        clearSwipeBitmaps()
                    }
                    pageVelocityTracker?.recycle()
                    pageVelocityTracker = null
                    gestureAxis = 0
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    settleDraggedWeek(0, 0f)
                    pageVelocityTracker?.recycle()
                    pageVelocityTracker = null
                    gestureAxis = 0
                    return true
                }
            }
            return true
        }

        fun setWeekIndex(index: Int) { clearSwipeBitmaps(); weekIndex = index; invalidate() }
        fun setScheduleMode(mode: ScheduleMode) { clearSwipeBitmaps(); scheduleMode = mode; invalidate() }
        fun setCourses(updated: List<Course>) { clearSwipeBitmaps(); courses = updated; invalidate() }

        fun releaseTransientCaches() {
            pageAnimator?.removeAllUpdateListeners()
            pageAnimator?.removeAllListeners()
            pageAnimator?.cancel()
            pageAnimator = null
            pageVelocityTracker?.recycle()
            pageVelocityTracker = null
            dragOffset = 0f
            clearSwipeBitmaps()
        }

        override fun onDraw(canvas: Canvas) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val currentNode = cachedCurrentNode
                if (currentNode != null && cachedCurrentWeek == weekIndex) {
                    drawCachedNode(canvas, currentNode, dragOffset)
                    cachedAdjacentNode?.let { adjacent ->
                        when {
                            cachedAdjacentWeek > weekIndex -> drawCachedNode(canvas, adjacent, desiredWidth + dragOffset)
                            cachedAdjacentWeek < weekIndex -> drawCachedNode(canvas, adjacent, -desiredWidth + dragOffset)
                        }
                    }
                    return
                }
            }
            val cached = cachedCurrentPage
            if (cached != null && cachedCurrentWeek == weekIndex) {
                canvas.drawBitmap(cached, dragOffset, 0f, pageBitmapPaint)
                val adjacent = cachedAdjacentPage
                if (adjacent != null) {
                    if (cachedAdjacentWeek > weekIndex) {
                        canvas.drawBitmap(adjacent, desiredWidth + dragOffset, 0f, pageBitmapPaint)
                    } else if (cachedAdjacentWeek < weekIndex) {
                        canvas.drawBitmap(adjacent, -desiredWidth + dragOffset, 0f, pageBitmapPaint)
                    }
                }
                return
            }
            drawWeekPage(canvas, weekIndex, dragOffset)
            if (dragOffset < 0f && weekIndex < 20) {
                drawWeekPage(canvas, weekIndex + 1, desiredWidth + dragOffset)
            } else if (dragOffset > 0f && weekIndex > 0) {
                drawWeekPage(canvas, weekIndex - 1, -desiredWidth + dragOffset)
            }
        }

        private fun buildCoursePlacements(visibleCourses: List<Course>): List<CoursePlacement> {
            val result = mutableListOf<CoursePlacement>()

            fun overlaps(first: Course, second: Course): Boolean {
                if (first.day != second.day) return false
                val firstEnd = first.startSlot + first.slotCount
                val secondEnd = second.startSlot + second.slotCount
                return first.startSlot < secondEnd && second.startSlot < firstEnd
            }

            visibleCourses.filter { it.day in 0..6 && it.startSlot in 0..9 }
                .groupBy { it.day }
                .values
                .forEach { dayCourses ->
                    val sorted = dayCourses.sortedWith(
                        compareBy<Course> { it.startSlot }
                            .thenByDescending { it.slotCount }
                    )
                    val component = mutableListOf<Course>()

                    fun flushComponent() {
                        if (component.isEmpty()) return
                        val columnEnds = mutableListOf<Int>()
                        val assigned = mutableListOf<Pair<Course, Int>>()
                        component.forEach { course ->
                            val column = columnEnds.indexOfFirst { end -> end <= course.startSlot }
                                .let { if (it >= 0) it else columnEnds.size }
                            if (column == columnEnds.size) columnEnds += 0
                            columnEnds[column] = course.startSlot + course.slotCount
                            assigned += course to column
                        }
                        val columnCount = columnEnds.size
                        assigned.forEach { (course, column) ->
                            result += CoursePlacement(course, column, columnCount)
                        }
                        component.clear()
                    }

                    sorted.forEach { course ->
                        if (component.isNotEmpty() && component.none { overlaps(it, course) }) {
                            flushComponent()
                        }
                        component += course
                    }
                    flushComponent()
                }
            return result
        }

        private fun drawWeekPage(canvas: Canvas, week: Int, offset: Float) {
            val save = canvas.save()
            canvas.translate(offset, 0f)
            canvas.clipRect(0f, 0f, desiredWidth.toFloat(), desiredHeight.toFloat())
            drawHeaders(canvas, week); drawTimes(canvas)
            val visibleCourses = courses.filter { courseVisibleInWeek(it, week) }
            val placements = buildCoursePlacements(visibleCourses)
            val hasVisibleCourse = placements.isNotEmpty()
            placements.forEach { placement ->
                drawCourse(canvas, placement.course, placement.column, placement.columnCount)
            }
            if (!hasVisibleCourse) {
                val centerX = timeColumnWidth + (desiredWidth - timeColumnWidth) / 2f
                val groupCenterY = headerHeight + slotHeight * 4.5f
                drawScheduleEmptyState(canvas, centerX, groupCenterY, week == 0)
            }
            canvas.restoreToCount(save)
        }

        /**
         * 与考试、成绩空状态共用同一套视觉尺寸：158×132dp 插画、18dp 标题和 13dp 说明。
         * 课程表是单个 Canvas，因此这里用原生矢量绘制，避免额外位图占用和缩放失真。
         */
        private fun drawScheduleEmptyState(canvas: Canvas, centerX: Float, groupCenterY: Float, beforeTerm: Boolean) {
            val illustrationCenterY = groupCenterY - dp(28f)
            drawScheduleEmptyIllustration(canvas, centerX, illustrationCenterY, beforeTerm)
            val titleCenterY = illustrationCenterY + dp(89f)
            val descriptionCenterY = titleCenterY + dp(27f)
            drawCenteredText(
                canvas,
                if (beforeTerm) "还没有开学哦" else "本周暂无课程",
                centerX,
                titleCenterY,
                sp(18f),
                TEXT_PRIMARY,
                Typeface.BOLD
            )
            drawCenteredText(
                canvas,
                if (beforeTerm) "距离开学还有 ${daysUntilTermStart()} 天" else "尽情放松吧～",
                centerX,
                descriptionCenterY,
                sp(13f),
                TEXT_SECONDARY,
                Typeface.NORMAL
            )
        }

        private fun drawScheduleEmptyIllustration(canvas: Canvas, cx: Float, cy: Float, beforeTerm: Boolean) {
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = Color.argb(38, 255, 255, 255)
            canvas.drawCircle(cx, cy, dp(57f).toFloat(), paint)
            paint.color = Color.argb(18, 131, 140, 199)
            canvas.drawCircle(cx + dp(17f), cy - dp(8f), dp(42f).toFloat(), paint)
            if (beforeTerm) drawBeforeTermIllustration(canvas, cx, cy)
            else drawRelaxingWeekIllustration(canvas, cx, cy)
        }

        /** 未开学：一本等待翻开的课程册和即将升起的太阳。 */
        private fun drawBeforeTermIllustration(canvas: Canvas, cx: Float, cy: Float) {
            val save = canvas.save()
            canvas.rotate(-4f, cx, cy)
            rect.set(cx - dp(42f), cy - dp(35f), cx + dp(35f), cy + dp(38f))

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(27, 55, 72, 125)
            canvas.drawRoundRect(
                rect.left + dp(3f), rect.top + dp(4f), rect.right + dp(3f), rect.bottom + dp(4f),
                dp(14f).toFloat(), dp(14f).toFloat(), paint
            )
            paint.color = Color.argb(210, 252, 253, 255)
            canvas.drawRoundRect(rect, dp(14f).toFloat(), dp(14f).toFloat(), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1.2f).toFloat()
            paint.color = Color.argb(95, 126, 139, 179)
            canvas.drawRoundRect(rect, dp(14f).toFloat(), dp(14f).toFloat(), paint)

            // 课程册的彩色书脊和即将展开的页面。
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(131, 140, 199)
            canvas.drawRoundRect(
                rect.left, rect.top, rect.left + dp(13f), rect.bottom,
                dp(12f).toFloat(), dp(12f).toFloat(), paint
            )
            paint.color = Color.rgb(245, 108, 126)
            canvas.drawRoundRect(
                rect.left + dp(20f), rect.top + dp(17f), rect.right - dp(10f), rect.top + dp(22f),
                dp(2.5f).toFloat(), dp(2.5f).toFloat(), paint
            )
            paint.color = Color.argb(105, 105, 113, 132)
            canvas.drawRoundRect(
                rect.left + dp(20f), rect.top + dp(31f), rect.right - dp(17f), rect.top + dp(34f),
                dp(1.5f).toFloat(), dp(1.5f).toFloat(), paint
            )
            canvas.drawRoundRect(
                rect.left + dp(20f), rect.top + dp(42f), rect.right - dp(12f), rect.top + dp(45f),
                dp(1.5f).toFloat(), dp(1.5f).toFloat(), paint
            )
            paint.color = Color.rgb(130, 173, 247)
            val bookmark = Path().apply {
                moveTo(rect.right - dp(22f), rect.bottom - dp(15f))
                lineTo(rect.right - dp(10f), rect.bottom - dp(15f))
                lineTo(rect.right - dp(16f), rect.bottom - dp(8f))
                close()
            }
            canvas.drawPath(bookmark, paint)
            canvas.restoreToCount(save)

            // 太阳从课程册右上角升起，表达“即将开学”。
            val sunX = cx + dp(38f)
            val sunY = cy - dp(31f)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(248, 180, 92)
            canvas.drawCircle(sunX, sunY, dp(9f).toFloat(), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2f).toFloat()
            paint.color = Color.argb(180, 248, 180, 92)
            for (angle in 0 until 360 step 45) {
                val radians = Math.toRadians(angle.toDouble())
                canvas.drawLine(
                    sunX + (kotlin.math.cos(radians) * dp(13f)).toFloat(),
                    sunY + (kotlin.math.sin(radians) * dp(13f)).toFloat(),
                    sunX + (kotlin.math.cos(radians) * dp(17f)).toFloat(),
                    sunY + (kotlin.math.sin(radians) * dp(17f)).toFloat(),
                    paint
                )
            }
        }

        /** 本周无课：一杯饮品、合上的书和柔和的小叶片。 */
        private fun drawRelaxingWeekIllustration(canvas: Canvas, cx: Float, cy: Float) {
            val save = canvas.save()
            canvas.rotate(4f, cx, cy)
            rect.set(cx - dp(43f), cy - dp(31f), cx + dp(30f), cy + dp(39f))
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(27, 55, 72, 125)
            canvas.drawRoundRect(
                rect.left + dp(3f), rect.top + dp(4f), rect.right + dp(3f), rect.bottom + dp(4f),
                dp(13f).toFloat(), dp(13f).toFloat(), paint
            )
            paint.color = Color.argb(210, 252, 253, 255)
            canvas.drawRoundRect(rect, dp(13f).toFloat(), dp(13f).toFloat(), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1.2f).toFloat()
            paint.color = Color.argb(95, 126, 139, 179)
            canvas.drawRoundRect(rect, dp(13f).toFloat(), dp(13f).toFloat(), paint)

            // 两本简洁叠放的书，与参考图保持一致。
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(131, 140, 199)
            canvas.drawRoundRect(
                cx - dp(31f), cy + dp(8f), cx + dp(13f), cy + dp(25f),
                dp(6f).toFloat(), dp(6f).toFloat(), paint
            )
            paint.color = Color.rgb(130, 173, 247)
            canvas.drawRoundRect(
                cx - dp(27f), cy + dp(3f), cx + dp(17f), cy + dp(17f),
                dp(5f).toFloat(), dp(5f).toFloat(), paint
            )
            paint.color = Color.argb(205, 252, 253, 255)
            canvas.drawRoundRect(
                cx - dp(24f), cy + dp(7f), cx + dp(13f), cy + dp(11f),
                dp(2f).toFloat(), dp(2f).toFloat(), paint
            )
            canvas.restoreToCount(save)

            // 热饮悬在书边，蒸汽强化轻松、休息的氛围。
            val cupX = cx + dp(28f)
            val cupY = cy - dp(5f)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(245, 108, 126)
            canvas.drawRoundRect(
                cupX - dp(13f), cupY - dp(4f), cupX + dp(11f), cupY + dp(20f),
                dp(7f).toFloat(), dp(7f).toFloat(), paint
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(3f).toFloat()
            paint.color = Color.rgb(245, 108, 126)
            canvas.drawArc(
                cupX + dp(4f), cupY, cupX + dp(20f), cupY + dp(16f),
                -85f, 170f, false, paint
            )
            paint.strokeWidth = dp(1.8f).toFloat()
            paint.color = Color.argb(150, 131, 140, 199)
            canvas.drawArc(cupX - dp(8f), cupY - dp(20f), cupX, cupY - dp(3f), 155f, 135f, false, paint)
            canvas.drawArc(cupX + dp(2f), cupY - dp(23f), cupX + dp(10f), cupY - dp(5f), 155f, 135f, false, paint)

            // 两片小叶子与现有彩色插画的点缀语言保持一致。
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(105, 205, 185)
            canvas.save()
            canvas.rotate(-28f, cx - dp(33f), cy - dp(31f))
            canvas.drawOval(
                cx - dp(40f), cy - dp(36f), cx - dp(27f), cy - dp(27f), paint
            )
            canvas.restore()
            paint.color = Color.rgb(130, 173, 247)
            canvas.save()
            canvas.rotate(26f, cx - dp(22f), cy - dp(36f))
            canvas.drawOval(
                cx - dp(28f), cy - dp(40f), cx - dp(16f), cy - dp(32f), paint
            )
            canvas.restore()
        }

        private fun settleDraggedWeek(delta: Int, releaseVelocityX: Float) {
            pageAnimator?.cancel()
            val target = when {
                delta > 0 -> -desiredWidth.toFloat()
                delta < 0 -> desiredWidth.toFloat()
                else -> 0f
            }
            val remainingDistance = kotlin.math.abs(target - dragOffset)
            val distanceRatio = remainingDistance / desiredWidth.coerceAtLeast(1)
            val velocityTowardTarget = when {
                target < dragOffset && releaseVelocityX < 0f -> -releaseVelocityX
                target > dragOffset && releaseVelocityX > 0f -> releaseVelocityX
                else -> 0f
            }
            val distanceDuration = (180f + 110f * distanceRatio).toLong()
            val velocityDuration = if (velocityTowardTarget >= pageMinimumFlingVelocity) {
                (remainingDistance / velocityTowardTarget * 880f).toLong().coerceIn(140L, 275L)
            } else {
                distanceDuration
            }
            pageAnimator = ValueAnimator.ofFloat(dragOffset, target).apply {
                duration = minOf(distanceDuration, velocityDuration).coerceIn(140L, 295L)
                interpolator = pageSettleInterpolator
                addUpdateListener {
                    dragOffset = it.animatedValue as Float
                    postInvalidateOnAnimation()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (delta != 0) {
                            weekIndex = (weekIndex + delta).coerceIn(0, 20)
                            currentWeek = weekIndex
                            scheduleWeek?.text = formatWeekLabel(currentWeek)
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        dragOffset = 0f
                        pageAnimator = null
                        clearSwipeBitmaps()
                        invalidate()
                    }
                })
                start()
            }
        }

        private fun resistedEdgeOffset(distance: Float): Float {
            val magnitude = kotlin.math.abs(distance)
            val limit = desiredWidth * .16f
            val resisted = limit * magnitude / (magnitude + desiredWidth * .72f).coerceAtLeast(1f)
            return if (distance < 0f) -resisted else resisted
        }

        private fun prepareSwipeBitmaps(adjacentWeek: Int) {
            if (desiredWidth <= 0 || desiredHeight <= 0) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                prepareSwipeRenderNodes(adjacentWeek)
                return
            }
            if (cachedCurrentPage == null || cachedCurrentWeek != weekIndex) {
                clearSwipeBitmaps()
                cachedCurrentPage = renderWeekBitmap(weekIndex)
                cachedCurrentWeek = if (cachedCurrentPage != null) weekIndex else -1
            }
            if (adjacentWeek !in 0..20) return
            if (cachedAdjacentPage == null || cachedAdjacentWeek != adjacentWeek) {
                cachedAdjacentPage?.recycle()
                cachedAdjacentPage = renderWeekBitmap(adjacentWeek)
                cachedAdjacentWeek = if (cachedAdjacentPage != null) adjacentWeek else -1
            }
        }

        private fun renderWeekBitmap(week: Int): Bitmap? = runCatching {
            Bitmap.createBitmap(desiredWidth, desiredHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                drawWeekPage(Canvas(bitmap), week, 0f)
            }
        }.getOrNull()

        @android.annotation.TargetApi(Build.VERSION_CODES.Q)
        private fun prepareSwipeRenderNodes(adjacentWeek: Int) {
            if (cachedCurrentNode == null || cachedCurrentWeek != weekIndex) {
                clearSwipeBitmaps()
                cachedCurrentNode = renderWeekNode(weekIndex)
                cachedCurrentWeek = if (cachedCurrentNode != null) weekIndex else -1
            }
            if (adjacentWeek !in 0..20) return
            if (cachedAdjacentNode == null || cachedAdjacentWeek != adjacentWeek) {
                cachedAdjacentNode?.discardDisplayList()
                cachedAdjacentNode = renderWeekNode(adjacentWeek)
                cachedAdjacentWeek = if (cachedAdjacentNode != null) adjacentWeek else -1
            }
        }

        @android.annotation.TargetApi(Build.VERSION_CODES.Q)
        private fun renderWeekNode(week: Int): RenderNode? = runCatching {
            RenderNode("schedule-week-$week").apply {
                setPosition(0, 0, desiredWidth, desiredHeight)
                val recordingCanvas = beginRecording()
                try {
                    drawWeekPage(recordingCanvas, week, 0f)
                } finally {
                    endRecording()
                }
            }
        }.getOrNull()

        @android.annotation.TargetApi(Build.VERSION_CODES.Q)
        private fun drawCachedNode(canvas: Canvas, node: RenderNode, offset: Float) {
            val save = canvas.save()
            canvas.translate(offset, 0f)
            canvas.drawRenderNode(node)
            canvas.restoreToCount(save)
        }

        private fun clearSwipeBitmaps() {
            cachedCurrentPage?.recycle()
            cachedAdjacentPage?.recycle()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cachedCurrentNode?.discardDisplayList()
                cachedAdjacentNode?.discardDisplayList()
            }
            cachedCurrentPage = null
            cachedAdjacentPage = null
            cachedCurrentNode = null
            cachedAdjacentNode = null
            cachedCurrentWeek = -1
            cachedAdjacentWeek = -1
        }

        override fun onDetachedFromWindow() {
            releaseTransientCaches()
            super.onDetachedFromWindow()
        }

        private fun drawHeaders(canvas: Canvas, week: Int) {
            val monthDate = displayWeekBaseDate(week)
            val today = Calendar.getInstance()
            val month = monthDate.get(Calendar.MONTH) + 1
            val monthSize = fittedGridTextSize(month.toString(), sp(14f), timeColumnWidth - dp(6f), Typeface.BOLD)
            drawCenteredText(canvas, month.toString(), timeColumnWidth / 2f, dp(14f).toFloat(), monthSize, TEXT_PRIMARY, Typeface.BOLD)
            drawCenteredText(canvas, "月", timeColumnWidth / 2f, dp(31f).toFloat(), sp(9f), TEXT_PRIMARY, Typeface.NORMAL)
            for (day in 0..6) {
                val center = timeColumnWidth + day * dayColumnWidth + dayColumnWidth / 2f
                val headerDate = monthDate.clone() as Calendar
                headerDate.add(Calendar.DAY_OF_MONTH, day)
                val isToday = headerDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    headerDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                val textColor = if (isToday) Color.rgb(22, 27, 39) else Color.rgb(142, 149, 166)
                drawCenteredText(
                    canvas,
                    dayNames[day],
                    center,
                    dp(14f).toFloat(),
                    sp(if (isToday) 12.6f else 11.7f),
                    textColor,
                    if (isToday) Typeface.BOLD else Typeface.NORMAL
                )
                drawCenteredText(
                    canvas,
                    dateForDay(day, week),
                    center,
                    dp(31f).toFloat(),
                    sp(if (isToday) 8.6f else 7.8f),
                    textColor,
                    if (isToday) Typeface.BOLD else Typeface.NORMAL
                )
            }
        }

        private fun dateForDay(day: Int, week: Int): String {
            val date = displayWeekBaseDate(week).apply { add(Calendar.DAY_OF_MONTH, day) }
            return SimpleDateFormat("M/d", Locale.CHINA).format(date.time)
        }

        private fun displayWeekBaseDate(week: Int): Calendar {
            if (week == 0) {
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                while (today.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) today.add(Calendar.DAY_OF_MONTH, -1)
                return today
            }
            return termStartDate(selectedTerm()).apply { add(Calendar.DAY_OF_MONTH, (week - 1) * 7) }
        }

        private fun drawTimes(canvas: Canvas) {
            val times = if (scheduleMode == ScheduleMode.SPRING) springTimes else summerTimes
            for (slot in 0..9) {
                val top = headerHeight + slot * slotHeight
                val center = timeColumnWidth / 2f
                val slotLabel = (slot + 1).toString()
                val slotSize = fittedGridTextSize(slotLabel, sp(15f), timeColumnWidth - dp(6f), Typeface.BOLD)
                val timeSize = minOf(
                    fittedGridTextSize(times[slot][0], sp(10f), timeColumnWidth - dp(4f), Typeface.NORMAL),
                    fittedGridTextSize(times[slot][1], sp(10f), timeColumnWidth - dp(4f), Typeface.NORMAL)
                )
                drawCenteredText(canvas, slotLabel, center, (top + dp(15f)).toFloat(), slotSize, TEXT_PRIMARY, Typeface.BOLD)
                drawCenteredText(canvas, times[slot][0], center, (top + dp(30f)).toFloat(), timeSize, TEXT_PRIMARY, Typeface.NORMAL)
                drawCenteredText(canvas, times[slot][1], center, (top + dp(43f)).toFloat(), timeSize, TEXT_PRIMARY, Typeface.NORMAL)
            }
        }

        private fun fittedGridTextSize(value: String, desiredSize: Float, maxWidth: Int, style: Int): Float {
            paint.textSize = desiredSize
            paint.typeface = Typeface.create(Typeface.DEFAULT, style)
            val measured = paint.measureText(value)
            return if (measured > maxWidth && measured > 0f) desiredSize * maxWidth / measured else desiredSize
        }

        private fun drawCourse(canvas: Canvas, course: Course, column: Int = 0, columnCount: Int = 1) {
            val dayLeft = timeColumnWidth + course.day * dayColumnWidth
            val horizontalGap = if (columnCount > 1) dp(2f).toFloat() else 0f
            val availableWidth = dayColumnWidth - dp(4f).toFloat()
            val courseWidth = (availableWidth - horizontalGap * (columnCount - 1)) /
                columnCount.coerceAtLeast(1)
            val left = dayLeft + dp(2f) + column * (courseWidth + horizontalGap)
            val top = headerHeight + course.startSlot * slotHeight + dp(2f)
            val right = left + courseWidth
            val bottom = headerHeight + (course.startSlot + course.slotCount) * slotHeight - dp(2f)
            rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            paint.style = Paint.Style.FILL; paint.color = course.background
            val corner = minOf(dp(9f).toFloat(), dayColumnWidth * .12f); canvas.drawRoundRect(rect, corner, corner, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f).toFloat(); paint.color = Color.argb(175, 255, 255, 255); canvas.drawRoundRect(rect, corner, corner, paint)
            val padding = maxOf(dp(3f).toFloat(), minOf(dp(7f).toFloat(), dayColumnWidth * .075f))
            // 使用卡片的真实内部宽度。列宽还包含左右各 3dp 的卡片外边距，
            // 若直接使用 dayColumnWidth，W/M 等宽字形会被误判为能够放下并越界。
            val maxWidth = (right - left).toFloat() - padding * 2f - dp(1f)
            val maxHeight = bottom - top - padding * 2
            var size = minOf(sp(12f), dayColumnWidth * .22f)
            var lines = wrapCourseLines(course, size, maxWidth)
            while (size > sp(7f) && lines.size * size * 1.16f > maxHeight) {
                size -= sp(.5f)
                lines = wrapCourseLines(course, size, maxWidth)
            }
            paint.style = Paint.Style.FILL
            paint.textSize = size
            paint.color = course.foreground
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            var baseline = top + padding - paint.ascent()
            val lineHeight = size * 1.16f
            lines.forEach { line ->
                if (baseline <= bottom - padding) {
                    paint.textSize = size
                    val measuredWidth = paint.measureText(line)
                    if (measuredWidth > maxWidth && measuredWidth > 0f) {
                        paint.textSize = size * maxWidth / measuredWidth
                    }
                    canvas.drawText(line, left + padding, baseline, paint)
                    baseline += lineHeight
                }
            }
        }

        private fun findCourseAt(x: Float, y: Float): Course? {
            if (y < headerHeight) return null
            return buildCoursePlacements(courses.filter { courseVisibleInWeek(it, weekIndex) })
                .firstOrNull { placement ->
                val course = placement.course
                val dayLeft = timeColumnWidth + course.day * dayColumnWidth
                val horizontalGap = if (placement.columnCount > 1) dp(2f).toFloat() else 0f
                val availableWidth = dayColumnWidth - dp(4f).toFloat()
                val courseWidth = (availableWidth - horizontalGap * (placement.columnCount - 1)) /
                    placement.columnCount.coerceAtLeast(1)
                val left = dayLeft + dp(2f) + placement.column * (courseWidth + horizontalGap)
                val top = headerHeight + course.startSlot * slotHeight + dp(2f)
                val right = left + courseWidth
                val bottom = headerHeight + (course.startSlot + course.slotCount) * slotHeight - dp(2f)
                x in left.toFloat()..right.toFloat() && y in top.toFloat()..bottom.toFloat()
            }?.course
        }

        private fun wrapCourseLines(course: Course, size: Float, maxWidth: Float): List<String> {
            paint.textSize = size
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val result = mutableListOf<String>()
            fun appendWrapped(raw: String, characterLimit: Int? = null) {
                raw.split('\n').forEach { paragraph ->
                    var remaining = paragraph
                    if (remaining.isEmpty()) result.add("")
                    while (remaining.isNotEmpty()) {
                        val measuredCount = paint.breakText(remaining, true, maxWidth, null)
                        val count = characterLimit?.let { minOf(measuredCount, it) } ?: measuredCount
                        if (count <= 0) break
                        result.add(remaining.substring(0, count))
                        remaining = remaining.substring(count)
                    }
                }
            }
            // 课程名最多三个字符一行，避免窄列里四个汉字紧贴卡片边缘。
            appendWrapped(course.name, characterLimit = 3)
            formatRoom(course.room).split('\n').forEach { roomLine ->
                val compactCode = roomLine.length <= 6 && roomLine.matches(Regex("^@[A-Za-z0-9-]+$"))
                val completeBuilding = roomLine.matches(Regex("^\\d+号楼$"))
                if (compactCode || completeBuilding) result.add(roomLine) else appendWrapped(roomLine)
            }
            appendWrapped(course.teacher)
            return result
        }

        private fun drawCenteredText(canvas: Canvas, value: String, centerX: Float, centerY: Float, size: Float, color: Int, style: Int) {
            paint.style = Paint.Style.FILL; paint.textSize = size; paint.color = color; paint.typeface = Typeface.create(Typeface.DEFAULT, style); paint.textAlign = Paint.Align.CENTER
            val metrics = paint.fontMetrics; canvas.drawText(value, centerX, centerY - (metrics.ascent + metrics.descent) / 2f, paint); paint.textAlign = Paint.Align.LEFT
        }

        private fun drawLeftText(canvas: Canvas, value: String, x: Float, centerY: Float, size: Float, color: Int, style: Int) {
            paint.style = Paint.Style.FILL; paint.textSize = size; paint.color = color; paint.typeface = Typeface.create(Typeface.DEFAULT, style); paint.textAlign = Paint.Align.LEFT
            val metrics = paint.fontMetrics
            canvas.drawText(value, x, centerY - (metrics.ascent + metrics.descent) / 2f, paint)
        }

        // 课程表网格尺寸固定，因此字号使用 density，不跟随系统 fontScale 放大。
        private fun sp(value: Float) = value * resources.displayMetrics.density
    }

    private fun formatExportRoom(room: String): String {
        val normalized = room.replace(Regex("\\s+"), "")
        return if (normalized.startsWith("图信")) "@$normalized" else formatRoom(room)
    }

    private fun formatRoom(room: String): String {
        val normalized = room.replace(Regex("\\s+"), "")
        val numberedBuilding = Regex("^(北校|南校)(\\d+)号楼(\\d+)$").find(normalized)
        if (numberedBuilding != null) {
            return "@${numberedBuilding.groupValues[1]}\n${numberedBuilding.groupValues[2]}号楼\n${numberedBuilding.groupValues[3]}"
        }
        val mapBuilding = Regex("^图信(楼.*)$").find(normalized)
        if (mapBuilding != null) return "@图信\n${mapBuilding.groupValues[1]}"
        return "@$room"
    }

    private fun showCourseDetails(course: Course) {
        if (detailOverlay != null) return
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(145, 12, 18, 30))
            isClickable = true
            setOnClickListener { hideCourseDetails() }
        }
        val card = surfaceCard(dp(24f).toFloat()).apply {
            setCardBackgroundColor(SCHEDULE_BACKGROUND)
            setOnClickListener { }
        }
        val body = verticalLayout().apply { setPadding(dp(22), dp(20), dp(22), dp(20)) }
        val titleRow = horizontalLayout().apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(text(course.name, 21f, TEXT_PRIMARY, Typeface.BOLD), LinearLayout.LayoutParams(0, -2, 1f))
        if (!viewingPublicSchedule) {
            titleRow.addView(ImageButton(this).apply {
                setImageResource(R.drawable.ic_edit)
                contentDescription = "修改课程"
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { showCourseEditor(course) }
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
        }
        titleRow.addView(text("×", 28f, TEXT_SECONDARY, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
            setOnClickListener { hideCourseDetails() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        body.addView(titleRow, spacedParams(dp(14)))
        body.addView(detailLine("地点", "@${course.room}", R.drawable.ic_detail_location), spacedParams(dp(10)))
        body.addView(detailLine("教师", course.teacher, R.drawable.ic_detail_teacher), spacedParams(dp(10)))
        body.addView(detailLine("节次", "第 ${course.startSlot + 1}-${course.startSlot + course.slotCount} 节", R.drawable.ic_detail_time), spacedParams(dp(10)))
        body.addView(detailLine("周数", course.weeks, R.drawable.ic_detail_week), matchWrapParams())
        card.addView(body)
        val width = minOf(dp(360f), resources.displayMetrics.widthPixels - dp(36f))
        overlay.addView(card, FrameLayout.LayoutParams(width, -2, Gravity.CENTER))
        pageHost.addView(overlay, matchParentParams())
        detailOverlay = overlay
        overlay.alpha = 0f
        card.scaleX = .92f; card.scaleY = .92f
        overlay.animate().alpha(1f).setDuration(160).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    private fun showCourseEditor(course: Course) {
        if (viewingPublicSchedule) return
        detailOverlay?.let { pageHost.removeView(it); detailOverlay = null }
        if (editorOverlay != null) return
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(145, 12, 18, 30))
            isClickable = true
            setOnClickListener { hideCourseEditor() }
        }
        val card = surfaceCard(dp(24f).toFloat()).apply {
            setCardBackgroundColor(SCHEDULE_BACKGROUND)
            setOnClickListener { }
        }
        val body = verticalLayout().apply { setPadding(dp(20), dp(18), dp(20), dp(18)) }
        val titleRow = horizontalLayout().apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(text("修改课程", 20f, TEXT_PRIMARY, Typeface.BOLD), LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(text("×", 28f, TEXT_SECONDARY, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
            setOnClickListener { hideCourseEditor() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        body.addView(titleRow, spacedParams(dp(12)))

        val nameInput = editableCourseField("课程名", course.name)
        val roomInput = editableCourseField("地点", course.room)
        val teacherInput = editableCourseField("教师", course.teacher)
        val weeksInput = editableCourseField("周数", course.weeks)
        body.addView(nameInput.first, spacedParams(dp(10)))
        body.addView(roomInput.first, spacedParams(dp(10)))
        body.addView(teacherInput.first, spacedParams(dp(10)))
        body.addView(weeksInput.first, matchWrapParams())
        titleRow.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_check)
            contentDescription = "保存修改"
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener {
                updateCourseCache(
                    course,
                    nameInput.second.text?.toString().orEmpty(),
                    roomInput.second.text?.toString().orEmpty(),
                    teacherInput.second.text?.toString().orEmpty(),
                    weeksInput.second.text?.toString().orEmpty()
                )
                hideCourseEditor()
            }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        card.addView(body)
        val width = minOf(dp(360f), resources.displayMetrics.widthPixels - dp(36f))
        overlay.addView(card, FrameLayout.LayoutParams(width, -2, Gravity.CENTER))
        pageHost.addView(overlay, matchParentParams())
        editorOverlay = overlay

        overlay.alpha = 0f; card.scaleX = .92f; card.scaleY = .92f
        overlay.animate().alpha(1f).setDuration(160).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    private fun editableCourseField(label: String, value: String): Pair<TextInputLayout, TextInputEditText> {
        val box = inputBox(label)
        val input = input(InputType.TYPE_CLASS_TEXT).apply { setText(value) }
        box.addView(input)
        return box to input
    }

    private fun updateCourseCache(original: Course, name: String, room: String, teacher: String, weeks: String) {
        val updated = loadCourseCache().map { current ->
            if (current.day == original.day && current.startSlot == original.startSlot && current.name == original.name) {
                current.copy(name = name, room = room, teacher = teacher, weeks = weeks)
            } else current
        }
        val recolored = recolorCourses(updated)
        saveCourseCache(recolored)
        scheduleGrid?.setCourses(recolored)
    }

    private fun detailLine(label: String, value: String, iconRes: Int): View {
        val row = horizontalLayout().apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(PRIMARY_DARK)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }, LinearLayout.LayoutParams(dp(28), dp(28)))
        row.addView(text(label, 12f, PRIMARY_DARK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(8), 0)
        }, LinearLayout.LayoutParams(dp(58), -2))
        row.addView(text(value, 15f, TEXT_PRIMARY, Typeface.NORMAL).apply {
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun hideCourseDetails() {
        val overlay = detailOverlay ?: return
        overlay.animate().alpha(0f).setDuration(140).withEndAction {
            pageHost.removeView(overlay)
            detailOverlay = null
        }.start()
    }

    private fun hideCourseEditor() {
        val overlay = editorOverlay ?: return
        overlay.animate().alpha(0f).setDuration(140).withEndAction {
            pageHost.removeView(overlay)
            editorOverlay = null
        }.start()
    }

    override fun onDestroy() {
        scheduleGrid?.releaseTransientCaches()
        networkExecutor.shutdownNow()
        updateExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) scheduleGrid?.releaseTransientCaches()
    }

    override fun onLowMemory() {
        scheduleGrid?.releaseTransientCaches()
        super.onLowMemory()
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 3001
        private const val REMINDER_REQUEST_CODE = 3002
        private const val PREFS_NAME = "offline_login"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_PASSWORD = "password"
        private const val KEY_STUDENT_NAME = "student_name"
        private const val KEY_TERM = "term"
        private const val KEY_SCORE_TERM = "score_term"
        private const val KEY_SCHEDULE_MODE = "schedule_mode"
        private const val KEY_COURSES = "courses_cache"
        private const val KEY_PUBLIC_SCHEDULE_SYNCED_TERM = "public_schedule_synced_term"
        private const val KEY_SCORES = "scores_cache"
        private const val SCORE_STATS_SCOPE = "all_terms_v2"
        private const val KEY_EXAMS = "exams_cache"
        private const val KEY_COLOR_MAP = "course_color_map"
        private const val KEY_PUSH_ENABLED = "push_enabled"
        private const val KEY_BATTERY_PROMPTED = "battery_prompted"
        private const val KEY_UPDATE_STARTED_CODE = "update_started_code"
        private const val VERSION_URL = "https://raw.giteeusercontent.com/sleexy/onlinedata/raw/master/WeSDAU_Class_Schedule_version.json"
        private const val APK_URL = "https://gitee.com/sleexy/onlinedata/raw/master/ClassSchedule-modern.apk"
        private const val UPDATE_FILE_NAME = "WeSDAU课程表最新版本.apk"
        private const val OFFICIAL_TERM = "2026-2027-1"
        private const val OFFICIAL_TERM_START_YEAR = 2026
        private const val OFFICIAL_TERM_START_MONTH = Calendar.SEPTEMBER
        private const val OFFICIAL_TERM_START_DAY = 7
        private val COURSE_COLORS = intArrayOf(
            Color.rgb(130, 173, 247), Color.rgb(237, 184, 119), Color.rgb(120, 225, 208),
            Color.rgb(104, 154, 205), Color.rgb(232, 138, 117), Color.rgb(231, 121, 151),
            Color.rgb(118, 181, 238), Color.rgb(184, 167, 246),
            Color.rgb(205, 142, 190), Color.rgb(132, 176, 212), Color.rgb(222, 174, 104),
            Color.rgb(125, 190, 151), Color.rgb(196, 143, 137), Color.rgb(151, 170, 218),
            Color.rgb(222, 142, 125), Color.rgb(164, 142, 205), Color.rgb(111, 183, 198),
            Color.rgb(211, 157, 116), Color.rgb(145, 193, 151), Color.rgb(191, 151, 210),
            Color.rgb(120, 171, 207), Color.rgb(222, 158, 143), Color.rgb(156, 190, 126),
            Color.rgb(202, 141, 167), Color.rgb(137, 161, 207), Color.rgb(213, 181, 117),
            Color.rgb(132, 193, 184), Color.rgb(185, 153, 210)
        )
        private val PAGE_BACKGROUND = Color.rgb(244, 246, 252)
        private val PUBLIC_PAGE_BACKGROUND = Color.rgb(241, 243, 249)
        private val PUBLIC_SURFACE = Color.rgb(247, 248, 252)
        private val PUBLIC_CARD_OUTLINE = Color.rgb(220, 223, 232)
        private val PUBLIC_FIELD_DIVIDER = Color.rgb(194, 196, 204)
        private val PUBLIC_TOGGLE_BACKGROUND = Color.rgb(239, 241, 243)
        private val PUBLIC_TOGGLE_OUTLINE = Color.rgb(143, 199, 246)
        private val SCHEDULE_BACKGROUND = Color.rgb(238, 242, 250)
        private val GRADIENT_COLORS = intArrayOf(
            Color.rgb(243, 242, 249), // #F3F2F9
            Color.rgb(240, 241, 249), // #F0F1F9
            Color.rgb(235, 239, 248), // #EBEFF8
            Color.rgb(227, 235, 247), // #E3EBF7
            Color.rgb(217, 229, 244)  // #D9E5F4
        )
        private val GRADIENT_START = GRADIENT_COLORS.first()
        private val GRADIENT_END = GRADIENT_COLORS.last()
        private const val SURFACE = Color.WHITE
        private val TEXT_PRIMARY = Color.rgb(28, 34, 48)
        private val TEXT_SECONDARY = Color.rgb(102, 111, 133)
        private val PRIMARY = Color.rgb(76, 92, 196)
        private val PRIMARY_DARK = Color.rgb(50, 64, 153)
        private val PRIMARY_CONTAINER = Color.rgb(232, 235, 255)
        private val OUTLINE = Color.rgb(220, 225, 237)
        private val ERROR = Color.rgb(187, 48, 56)
    }
}
