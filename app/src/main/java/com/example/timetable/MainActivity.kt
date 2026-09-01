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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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

/**
 * 一门课程在界面展示所需的最小数据。
 *
 * 后续加入本地保存和教务系统导入时，会在此基础上补充教师、周次和上课时间等字段。
 */
private data class Course(val name: String, val place: String, val color: Color)

/**
 * 当前用于演示布局的固定课程数据。
 * 键的格式为“星期序号-节次”，例如“1-1”表示周一第 1 节。
 * 真实课程保存功能完成后，这里会由本地数据库中的数据替代。
 */
private val sampleCourses = mapOf(
    "1-1" to Course("离散数学", "B-302", Color(0xFFDCEBFF)),
    "2-2" to Course("数据库原理", "机房 406", Color(0xFFE0F5E9)),
    "3-3" to Course("计算机组成原理", "A-201", Color(0xFFFFE9C8)),
    "4-4" to Course("英语", "C-105", Color(0xFFF2E2FF)),
    "5-5" to Course("体育", "田径场", Color(0xFFFFE1E5)),
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
            // 目前按钮仅作为界面入口。下一阶段会在 onClick 中打开“添加课程”表单。
            FloatingActionButton(onClick = {}) {
                Text("＋", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { innerPadding ->
        // innerPadding 会避开顶部栏和系统区域，防止课程表被遮住。
        TimetableGrid(Modifier.fillMaxSize().padding(innerPadding))
    }
}

@Composable
private fun TimetableGrid(modifier: Modifier = Modifier) {
    // 使用可滚动的纵向容器：课程节次增多时，用户可以向下查看。
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // 先显示星期栏，再依序创建第 1 节到第 6 节的课程行。
        WeekdayHeader()
        Spacer(Modifier.height(6.dp))
        (1..6).forEach { period ->
            TimetableRow(period)
            Spacer(Modifier.height(6.dp))
        }
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
private fun TimetableRow(period: Int) {
    // 一行代表一个节次：左边是节次标签，右边是周一到周日的课程格。
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.size(width = 42.dp, height = 92.dp), contentAlignment = Alignment.Center) {
            Text("第${period}节", textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
        }
        weekdays.forEachIndexed { dayIndex, _ ->
            // dayIndex 从 0 开始，因此加 1 后才能和示例数据的星期序号对应。
            CourseCell(sampleCourses["${dayIndex + 1}-$period"])
        }
    }
}

@Composable
private fun CourseCell(course: Course?) {
    // 每格固定窄宽度，确保七天可以同时放进手机屏幕；文字不足时会向下换行。
    Box(Modifier.size(width = 46.dp, height = 92.dp).padding(horizontal = 2.dp)) {
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
