package com.example.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.timetable.ui.theme.TimetableTheme

/**
 * 课程表的列标题。一周固定显示七天，即使周末暂时没有课程也保留空格。
 */
private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 每天显示 12 节课；表格循环与添加表单都共用这个范围。 */
private const val PERIOD_COUNT = 12

/**
 * 一门课程在界面展示所需的最小数据。
 *
 * 后续加入本地保存和教务系统导入时，会在此基础上补充教师、周次和上课时间等字段。
 */
private data class Course(val name: String, val place: String, val color: Color)

/** 一次上课安排：同一门课可以覆盖同一天的连续多个节次。 */
private data class CourseEntry(
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val course: Course,
)

/**
 * 当前用于演示布局的固定课程数据。
 * 键的格式为“星期序号-节次”，例如“1-1”表示周一第 1 节。
 * 真实课程保存功能完成后，这里会由本地数据库中的数据替代。
 */
private val initialCourses = listOf(
    CourseEntry(1, 1, 1, Course("离散数学", "B-302", Color(0xFFDCEBFF))),
    CourseEntry(2, 2, 2, Course("数据库原理", "机房 406", Color(0xFFE0F5E9))),
    CourseEntry(3, 3, 3, Course("计算机组成原理", "A-201", Color(0xFFFFE9C8))),
    CourseEntry(4, 4, 4, Course("英语", "C-105", Color(0xFFF2E2FF))),
    CourseEntry(5, 5, 5, Course("体育", "田径场", Color(0xFFFFE1E5))),
)

private val courseColors = listOf(
    Color(0xFFDCEBFF), Color(0xFFE0F5E9), Color(0xFFFFE9C8), Color(0xFFF2E2FF), Color(0xFFFFE1E5),
)

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
    // 目前课程只保存在运行内存中；下一阶段会替换为本地数据库中的数据。
    var courses by remember { mutableStateOf(initialCourses) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Scaffold 提供页面的基础布局：顶部标题栏、主体内容区和右下角浮动按钮。
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // 第一行是页面名称；第二行显示当前学期和周次。
                        Text("我的课程表", fontWeight = FontWeight.Bold)
                        Text("2026-2027 学年第一学期 · 第 1 周", style = MaterialTheme.typography.labelMedium)
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
        TimetableGrid(courses, Modifier.fillMaxSize().padding(innerPadding))
    }

    if (showAddDialog) {
        AddCourseDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, place, day, start, end ->
                val color = courseColors[courses.size % courseColors.size]
                courses = courses + CourseEntry(day, start, end, Course(name, place, color))
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun TimetableGrid(courses: List<CourseEntry>, modifier: Modifier = Modifier) {
    // 使用可滚动的纵向容器：课程节次增多时，用户可以向下查看。
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // 先显示星期栏；下方按“星期列”绘制，连续课程可合并成一张加高卡片。
        WeekdayHeader()
        Spacer(Modifier.height(6.dp))
        TimetableBody(courses)
    }
}

@Composable
private fun WeekdayHeader() {
    // Row 让七个星期标题横向排列；左侧预留位置与下面的节次标签对齐。
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.size(width = 42.dp, height = 40.dp))
        weekdays.forEach { weekday ->
            Box(Modifier.size(width = 46.dp, height = 40.dp), contentAlignment = Alignment.Center) {
                Text(weekday, style = MaterialTheme.typography.labelLarge)
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
    Column(Modifier.size(width = 42.dp, height = 1170.dp)) {
        (1..PERIOD_COUNT).forEach { period ->
            Box(Modifier.size(width = 42.dp, height = 92.dp), contentAlignment = Alignment.Center) {
                Text("第${period}节", textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
            if (period < PERIOD_COUNT) Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun DayColumn(day: Int, courses: List<CourseEntry>) {
    Column(Modifier.size(width = 46.dp, height = 1170.dp)) {
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
            if (period <= PERIOD_COUNT) Spacer(Modifier.height(6.dp))
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
    val cellHeight = 92.dp * span + 6.dp * (span - 1)
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
                    Text(course.place, style = MaterialTheme.typography.labelSmall)
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
