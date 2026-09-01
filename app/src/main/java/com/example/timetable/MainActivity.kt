package com.example.timetable

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.os.Bundle
import android.util.Base64
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.timetable.ui.theme.TimetableTheme
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import androidx.core.app.NotificationCompat

/**
 * 课程表的列标题。一周固定显示七天，即使周末暂时没有课程也保留空格。
 */
private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 每天显示 12 节课；表格循环与添加表单都共用这个范围。 */
private const val PERIOD_COUNT = 12
private const val TOTAL_WEEKS = 17

/** 当前学期第 1 周的周一；后续周次的月日会从这里自动计算。 */
private const val SEMESTER_START_YEAR = 2026
private const val SEMESTER_START_MONTH = Calendar.AUGUST
private const val SEMESTER_START_DAY = 31

private fun dateLabel(week: Int, day: Int): String {
    val date = Calendar.getInstance().apply {
        set(SEMESTER_START_YEAR, SEMESTER_START_MONTH, SEMESTER_START_DAY)
        add(Calendar.DAY_OF_YEAR, (week - 1) * weekdays.size + day - 1)
    }
    return "${date.get(Calendar.MONTH) + 1}月${date.get(Calendar.DAY_OF_MONTH)}日"
}

/**
 * 原始 Excel 以“1-2 节、3-4 节……”为一组记录时间。
 * 每节都显示完整的上课区间，方便快速判断一节课的实际开始和结束时刻。
 */
private val periodTimeLabels = listOf(
    "08:30-09:15", "09:20-10:05", "10:25-11:10", "11:15-12:00",
    "14:00-14:45", "14:50-15:35", "15:45-16:30", "16:35-17:20",
    "18:00-18:45", "18:45-19:30", "19:30-20:15", "20:15-21:00",
)

/** 单节课的高度与节次间距。课表以 2 或 4 节连排为主，因此采用更紧凑的尺寸。 */
private val periodHeight = 62.dp
private val periodGap = 4.dp
private val timetableHeight = periodHeight * PERIOD_COUNT + periodGap * (PERIOD_COUNT - 1)

/**
 * 一门课程在界面展示所需的最小数据。
 *
 * 后续加入本地保存和教务系统导入时，会在此基础上补充教师、周次和上课时间等字段。
 */
private data class Course(val name: String, val place: String, val color: Color)

/** 一次上课安排：同一门课可以覆盖同一天的连续多个节次。 */
private data class CourseEntry(
    val week: Int,
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val course: Course,
)

private val courseColors = listOf(
    Color(0xFFDCEBFF), Color(0xFFE0F5E9), Color(0xFFFFE9C8), Color(0xFFF2E2FF), Color(0xFFFFE1E5),
)

private val courseCatalog = listOf(
    "计算机组成原理|11-411", "大学英语B|11-107", "数据库应用技术|11-110", "大学英语B|11-607",
    "数据库应用技术|11-511", "编译技术|11-210", "编译技术|11-110", "计算机组成原理|11-210",
    "离散数学A|11-202", "工程数学|11-405", "体能训练测试1|田径场", "Web开发技术|11-211",
    "Web开发技术|11-211", "数据库应用技术|信-105", "编译技术|信-105", "数据库应用技术|信-105",
    "Web开发技术|信-401", "Web开发技术|信-105", "计算机组成原理|信-401", "离散数学A|信-401",
    "嵌入式接口技术|11-211", "数据库应用技术|信-401", "Python程序设计与应用A|11-202",
    "Python程序设计与应用A|11-202", "嵌入式接口技术|信-105", "Python程序设计与应用A|信-401",
)

/**
 * 从用户提供的 17 周 Excel 课表提取并压缩后的固定数据。
 *
 * 必须放在课程目录 [courseCatalog] 的后面初始化：导入时需要先按编号查目录。
 * 这样应用启动时课程目录已经准备完成，不会出现空指针崩溃。
 */
private val initialCourses = loadImportedCourses()

private const val REMINDER_CHANNEL_ID = "class_reminders"
private const val REMINDER_ACTION = "com.example.timetable.CLASS_REMINDER"
private const val REMINDER_LEAD_MINUTES = 40

/** 本地保存手动课程使用的文件名和键名。数据只保存在本机，不会上传网络。 */
private const val LOCAL_PREFS = "timetable_local_data"
private const val MANUAL_COURSES_KEY = "manual_courses"

private fun loadImportedCourses(): List<CourseEntry> {
    val encoded = "eNrFk1sSwiAMRTeUjyYQWoatuAb3/2l4WJ0gLVQcv24m0HvyoAgIBEugpBhMUgo2qQkIBixYyUdlyUd1gZOucs7gYJPvo3o5z8o5f7tjJ4CK8RnAiG6AyziJcx4rZO4N6ffNVaQGoe3cdNROCF7SgKb2Oh3I9Dl8P/qydqvZe58sV3Lk/vAUgsv1rTPYwzN4wt+GYUs08Eh7+f6Iv0pA8efcg2Ydmn+MJfywekVr9qqbJdO92K6WxjGlKTvGUxziF+g6oNs27X6S724zWo66/QAOG1YB"
    val rows = InflaterInputStream(ByteArrayInputStream(Base64.decode(encoded, Base64.DEFAULT))).bufferedReader().use { it.readText() }
    // 压缩文本以字符“\\n”分隔每一周；不能用 lineSequence()，否则它会被当作同一行。
    return rows.split("\\n").asSequence().flatMapIndexed { weekIndex, line ->
        line.split(';').asSequence().filter { it.isNotBlank() }.map { item ->
            val (day, start, end, catalogIndex) = item.split(',').map { it.toInt() }
            val (name, place) = courseCatalog[catalogIndex].split('|', limit = 2)
            CourseEntry(weekIndex + 1, day, start, end, Course(name, place, courseColors[catalogIndex % courseColors.size]))
        }
    }.toList()
}

/** 从 SharedPreferences 读取上次手动添加的课程；数据损坏时返回空列表，保证 App 仍能打开。 */
private fun loadSavedCourses(context: Context): List<CourseEntry> {
    val text = context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
        .getString(MANUAL_COURSES_KEY, null) ?: return emptyList()
    return runCatching {
        val json = JSONArray(text)
        buildList {
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                val color = courseColors[index % courseColors.size]
                add(
                    CourseEntry(
                        week = item.getInt("week"),
                        day = item.getInt("day"),
                        startPeriod = item.getInt("startPeriod"),
                        endPeriod = item.getInt("endPeriod"),
                        course = Course(item.getString("name"), item.getString("place"), color),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

/** 把手动课程序列化为简单 JSON，应用重启后可以恢复。 */
private fun saveCourses(context: Context, courses: List<CourseEntry>) {
    val json = JSONArray()
    courses.forEach { entry ->
        json.put(
            JSONObject().apply {
                put("week", entry.week)
                put("day", entry.day)
                put("startPeriod", entry.startPeriod)
                put("endPeriod", entry.endPeriod)
                put("name", entry.course.name)
                put("place", entry.course.place)
            },
        )
    }
    context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(MANUAL_COURSES_KEY, json.toString())
        .apply()
}

/** 将节次转换为当天分钟数，用于计算“上课前 40 分钟”的通知时间。 */
private val periodStartMinutes = listOf(510, 560, 625, 675, 840, 890, 945, 995, 1080, 1125, 1170, 1215)

private fun createReminderChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "课程提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "上课前 40 分钟提醒课程和教室" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/** 为每个周次、星期和时段安排一条通知；没有课程的时段不会创建通知。 */
private fun scheduleClassReminders(context: Context, courses: List<CourseEntry>) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val now = System.currentTimeMillis()
    var requestCode = 1000
    val segments = listOf(
        Triple("上午", 1, 4),
        Triple("下午", 5, 8),
        Triple("晚上", 9, 12),
    )

    for (week in 1..TOTAL_WEEKS) {
        for (day in weekdays.indices) {
            for ((segmentName, firstPeriod, lastPeriod) in segments) {
                val segmentCourses = courses
                    .filter { it.week == week && it.day == day + 1 && it.startPeriod in firstPeriod..lastPeriod }
                    .sortedBy { it.startPeriod }
                val firstCourse = segmentCourses.firstOrNull() ?: continue
                val firstStart = periodStartMinutes[firstCourse.startPeriod - 1]
                val date = Calendar.getInstance().apply {
                    set(SEMESTER_START_YEAR, SEMESTER_START_MONTH, SEMESTER_START_DAY, firstStart / 60, firstStart % 60, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, (week - 1) * weekdays.size + day)
                    add(Calendar.MINUTE, -REMINDER_LEAD_MINUTES)
                }
                if (date.timeInMillis <= now) {
                    requestCode += 1
                    continue
                }
                val courseText = segmentCourses.joinToString("；") { "${it.course.name}（${it.course.place}）" }
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    action = REMINDER_ACTION
                    putExtra("notificationId", requestCode)
                    putExtra("title", "40分钟后上课 · $segmentName")
                    putExtra("text", courseText)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, date.timeInMillis, pendingIntent)
                requestCode += 1
            }
        }
    }
}

/** 系统到点后接收闹钟，并显示课程提醒通知。 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        createReminderChannel(context)
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(intent.getStringExtra("title") ?: "课程提醒")
            .setContentText(intent.getStringExtra("text") ?: "请查看课程表")
            .setStyle(NotificationCompat.BigTextStyle().bigText(intent.getStringExtra("text") ?: "请查看课程表"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val id = intent.getIntExtra("notificationId", 1)
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // setContent 是 Compose 界面的入口：将课程表页面放入应用的统一主题中。
        setContent { TimetableTheme { TimetableApp() } }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TimetableApp() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        createReminderChannel(context)
        scheduleClassReminders(context, initialCourses + loadSavedCourses(context))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            (context as? ComponentActivity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
    // 固定导入课表与用户手动添加的课程分开管理；手动课程从本地恢复，重启后仍然存在。
    var manualCourses by remember { mutableStateOf(loadSavedCourses(context)) }
    val courses = initialCourses + manualCourses
    var showAddDialog by remember { mutableStateOf(false) }
    var currentWeek by remember { mutableStateOf(1) }
    // 拖动中的实时位移，让课表跟随手指移动；松手后归零并交给周次切换动画收尾。
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // Scaffold 提供页面的基础布局：顶部标题栏、主体内容区和右下角浮动按钮。
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // 第一行是页面名称；第二行显示当前学期和周次。
                        Text("我的课程表", fontWeight = FontWeight.Bold)
                        Text("2026-2027 学年第一学期 · 第 $currentWeek 周", style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            // 点击后显示录入表单；保存成功才会把新课程加入课程表。
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("＋", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { innerPadding ->
        // innerPadding 会避开顶部栏和系统区域，防止课程表被遮住。
        // AnimatedContent 根据周次变化方向，让下一周从右向左、上一周从左向右滑入。
        AnimatedContent(
            targetState = currentWeek,
            transitionSpec = {
                val goToNextWeek = targetState > initialState
                (
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> if (goToNextWeek) fullWidth else -fullWidth },
                        animationSpec = tween(280),
                    ) + fadeIn(animationSpec = tween(280))
                ).togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> if (goToNextWeek) -fullWidth else fullWidth },
                        animationSpec = tween(280),
                    ) + fadeOut(animationSpec = tween(180)),
                )
            },
            label = "weekSwitchAnimation",
        ) { displayedWeek ->
            TimetableGrid(
                courses = courses.filter { it.week == displayedWeek },
                week = displayedWeek,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // graphicsLayer 直接使用当前拖动距离，因此移动没有等待动画的延迟。
                    .graphicsLayer { translationX = dragOffset }
                    // 横向拖动超过阈值才切换，避免普通的手指抖动误触发换周。
                    .pointerInput(displayedWeek) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                                // 限制最大跟手距离，避免把整个表格拖离屏幕太远。
                                dragOffset = (dragOffset + dragAmount).coerceIn(-280f, 280f)
                            },
                            onDragEnd = {
                                when {
                                    totalDrag < -120f -> currentWeek = (currentWeek + 1).coerceAtMost(TOTAL_WEEKS)
                                    totalDrag > 120f -> currentWeek = (currentWeek - 1).coerceAtLeast(1)
                                }
                                dragOffset = 0f
                            },
                            onDragCancel = { dragOffset = 0f },
                        )
                    },
            )
        }
    }

    if (showAddDialog) {
        AddCourseDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, place, day, start, end ->
                val color = courseColors[manualCourses.size % courseColors.size]
                val newCourse = CourseEntry(currentWeek, day, start, end, Course(name, place, color))
                manualCourses = manualCourses + newCourse
                saveCourses(context, manualCourses)
                scheduleClassReminders(context, initialCourses + manualCourses)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun TimetableGrid(courses: List<CourseEntry>, week: Int, modifier: Modifier = Modifier) {
    // 使用可滚动的纵向容器：课程节次增多时，用户可以向下查看。
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // 先显示星期栏；下方按“星期列”绘制，连续课程可合并成一张加高卡片。
        WeekdayHeader(week)
        Spacer(Modifier.height(periodGap))
        TimetableBody(courses)
    }
}

@Composable
private fun WeekdayHeader(week: Int) {
    // Row 让七个星期标题横向排列；左侧预留位置与下面的节次标签对齐。
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.size(width = 42.dp, height = 40.dp))
        weekdays.forEach { weekday ->
            Box(Modifier.size(width = 46.dp, height = 40.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(weekday, style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = dateLabel(week, weekdays.indexOf(weekday) + 1),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimetableBody(courses: List<CourseEntry>) {
    // 左侧节次列和右侧七个星期列并排；每列自行计算课程卡片高度。
    Row(Modifier.fillMaxWidth()) {
        PeriodColumn()
        weekdays.indices.forEach { dayIndex ->
            DayColumn(dayIndex + 1, courses)
        }
    }
}

@Composable
private fun PeriodColumn() {
    Column(Modifier.size(width = 42.dp, height = timetableHeight)) {
        (1..PERIOD_COUNT).forEach { period ->
            Box(Modifier.size(width = 42.dp, height = periodHeight), contentAlignment = Alignment.Center) {
                // 时间不足一行时，只允许在“-”后换行：例如“08:30-”与“09:15”，不会截断数字。
                Text(
                    text = "第${period}节\n${periodTimeLabels[period - 1].replace("-", "-\u200B")}",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 3,
                )
            }
            if (period < PERIOD_COUNT) Spacer(Modifier.height(periodGap))
        }
    }
}

@Composable
private fun DayColumn(day: Int, courses: List<CourseEntry>) {
    Column(Modifier.size(width = 46.dp, height = timetableHeight)) {
        var period = 1
        while (period <= PERIOD_COUNT) {
            // 只在课程开始节次绘制卡片，并跳过它覆盖的后续节次。
            val entry = courses.lastOrNull { it.day == day && it.startPeriod == period }
            if (entry == null) {
                CourseCell(course = null, span = 1)
                period += 1
            } else {
                val span = entry.endPeriod - entry.startPeriod + 1
                CourseCell(course = entry.course, span = span)
                period += span
            }
            if (period <= PERIOD_COUNT) Spacer(Modifier.height(periodGap))
        }
    }
}

/**
 * 手动添加课程的表单。保存前会验证：课程名称不能为空，且节次必须在 1 到 12 之间。
 */
@Composable
private fun AddCourseDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, place: String, day: Int, start: Int, end: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(1) }
    var startText by remember { mutableStateOf("1") }
    var endText by remember { mutableStateOf("1") }
    val start = startText.toIntOrNull()
    val end = endText.toIntOrNull()
    val formValid = name.isNotBlank() && start != null && end != null && start in 1..PERIOD_COUNT && end in start..PERIOD_COUNT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加课程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("课程名称") }, singleLine = true)
                OutlinedTextField(value = place, onValueChange = { place = it }, label = { Text("上课地点（可留空）") }, singleLine = true)
                Text("星期")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    weekdays.forEachIndexed { index, label ->
                        FilterChip(
                            selected = day == index + 1,
                            onClick = { day = index + 1 },
                            label = { Text(label.takeLast(1)) },
                            modifier = Modifier.size(width = 42.dp, height = 32.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = startText, onValueChange = { startText = it }, label = { Text("开始节次") }, singleLine = true, modifier = Modifier.size(width = 130.dp, height = 56.dp))
                    OutlinedTextField(value = endText, onValueChange = { endText = it }, label = { Text("结束节次") }, singleLine = true, modifier = Modifier.size(width = 130.dp, height = 56.dp))
                }
                if (!formValid && (name.isNotBlank() || startText != "1" || endText != "1")) {
                    Text("请填写课程名称，节次范围为 1 至 12，且结束节次不能早于开始节次。", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim(), place.trim(), day, start!!, end!!) }, enabled = formValid) { Text("保存") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CourseCell(course: Course?, span: Int) {
    // span 是课程覆盖的连续节次数；一张卡片会占据这些节次及它们之间的间距。
    val cellHeight = periodHeight * span + periodGap * (span - 1)
    Box(Modifier.size(width = 46.dp, height = cellHeight).padding(horizontal = 2.dp)) {
        if (course == null) {
            // 没有课程时显示淡色空格，保留表格结构，方便后续点击添加。
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp)))
        } else {
            // 有课程时用带颜色的卡片显示课程名和上课地点。
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = course.color),
            ) {
                Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(course.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    // 例如“11-411”在空间充足时保持一行；空间不足时，零宽空格允许它只在“-”后换行。
                    Text(
                        text = course.place.replace("-", "-\u200B"),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimetableAppPreview() {
    // Preview 仅用于 Android Studio 预览，不会影响手机中实际运行的页面。
    TimetableTheme { TimetableApp() }
}
