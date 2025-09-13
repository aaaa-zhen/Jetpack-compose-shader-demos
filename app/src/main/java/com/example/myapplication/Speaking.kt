import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random


@Preview
@Composable
fun SoundVisualizer() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 状态变量
    var hasAudioPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    ) }
    var isRecording by remember { mutableStateOf(false) }

    // 使用animateDpAsState需要的目标高度列表
    val targetBarHeights = remember { mutableStateListOf<Dp>() }
    // 初始化5个柱状图的目标高度
    if (targetBarHeights.isEmpty()) {
        repeat(5) {
            targetBarHeights.add(10.dp)
        }
    }

    // 创建动画高度状态
    val animatedBarHeights = targetBarHeights.mapIndexed { index, targetHeight ->
        animateDpAsState(
            targetValue = targetHeight,
            animationSpec = spring(
                dampingRatio = 0.9f,
                stiffness = 300f
            ),

            label = "BarHeight$index"
        )
    }

    // 记录AudioRecord实例
    val audioRecord = remember { mutableStateOf<AudioRecord?>(null) }

    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            // 权限获取后自动开始录音
            startRecording(context, audioRecord, targetBarHeights, coroutineScope) {
                isRecording = it
            }
        }
    }

    // 主UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 可视化部分
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(60.dp) // 增加高度以容纳更大的柱状图
            ) {
                animatedBarHeights.forEachIndexed { index, animatedHeight ->
                    Box(
                        modifier = Modifier
                            .size(8.dp, animatedHeight.value)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFFFBF11), // Yellow/Orange color (FFBF11)
                                        Color(0xFFFC4CE1), // Pink color (FC4CE1)
                                        Color(0xFF8E5BFE)  // Purple color (8E5BFE)
                                    ),
                                    start = Offset(-20f, 0f),
                                    end = Offset(0f, Float.POSITIVE_INFINITY)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 控制按钮
            Button(
                onClick = {
                    if (!hasAudioPermission) {
                        // 请求权限
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        if (isRecording) {
                            // 停止录音
                            audioRecord.value?.stop()
                            audioRecord.value?.release()
                            audioRecord.value = null
                            isRecording = false
                            // 重置为默认高度
                            targetBarHeights.indices.forEach { i ->
                                targetBarHeights[i] = 12.dp
                            }
                        } else {
                            // 开始录音
                            startRecording(context, audioRecord, targetBarHeights, coroutineScope) {
                                isRecording = it
                            }
                        }
                    }
                }
            ) {
                Text(if (isRecording) "停止录音" else "开始录音")
            }
        }
    }
}

// 开始录音并处理音频数据
private fun startRecording(
    context: Context,
    audioRecord: MutableState<AudioRecord?>,
    targetBarHeights: MutableList<Dp>,
    scope: CoroutineScope,
    onRecordingStateChanged: (Boolean) -> Unit
) {
    // 音频配置
    val sampleRate = 44100
    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    try {
        // 检查权限
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 如果没有权限，直接返回
            onRecordingStateChanged(false)
            return
        }

        // 创建AudioRecord实例
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord.value = recorder
        recorder.startRecording()
        onRecordingStateChanged(true)

        // 设置噪音阈值（解决问题1：小声音不响应）
        val noiseThreshold = 350.0 // 调整这个值以适应环境噪音水平

        // 在协程中处理音频数据
        scope.launch {
            val buffer = ShortArray(bufferSize / 2)
            var silenceCounter = 0 // 静音计数器

            try {
                while (audioRecord.value != null && recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    val readSize = withContext(Dispatchers.IO) {
                        recorder.read(buffer, 0, buffer.size)
                    }

                    if (readSize > 0) {
                        // 计算音量级别
                        var sum = 0.0
                        for (i in 0 until readSize) {
                            sum += abs(buffer[i].toDouble())
                        }
                        val average = sum / readSize

                        // 将音量映射到视觉效果
                        withContext(Dispatchers.Main) {
                            updateVisualizerBars(targetBarHeights, average, noiseThreshold)

                            // 跟踪静音时间
                            if (average < noiseThreshold) {
                                silenceCounter++
                                // 如果连续10个检测周期都很安静，重置柱状图为静止状态
                                if (silenceCounter > 10) {
                                    resetBarsToDefault(targetBarHeights)
                                }
                            } else {
                                silenceCounter = 0
                            }
                        }
                    }

                    // 防止UI过度刷新
                    delay(50)
                }
            } catch (e: Exception) {
                // 错误处理
                withContext(Dispatchers.Main) {
                    onRecordingStateChanged(false)
                }
            }
        }
    } catch (e: Exception) {
        // 如果无法创建AudioRecord，通知UI
        onRecordingStateChanged(false)
    }
}

// 重置柱状图到默认状态的函数
private fun resetBarsToDefault(targetBarHeights: MutableList<Dp>) {
    // 所有柱图高度会逐渐回到默认值
    for (i in targetBarHeights.indices) {
        targetBarHeights[i] = 12.dp
    }
}

// 更新可视化条的高度
private fun updateVisualizerBars(
    targetBarHeights: MutableList<Dp>,
    audioLevel: Double,
    noiseThreshold: Double
) {
    // 问题1：忽略低于阈值的音频
    if (audioLevel < noiseThreshold) {
        return
    }

    // 音量归一化，映射到合适的dp范围
    val normalizedLevel = min(1.0, audioLevel / 3000.0)

    for (i in targetBarHeights.indices) {
        // 为每个柱添加更强的随机性，使得柱状图高度更加多样化
        val randomFactor = when (i) {
            0 -> 0.5 + Random.nextDouble(0.0, 0.25) // 第一根柱子
            1 -> 0.7 + Random.nextDouble(0.0, 0.6) // 第二根柱子
            2 -> 0.8 + Random.nextDouble(0.0, 0.8) // 第三根柱子（中间柱子可以最高）
            3 -> 0.7 + Random.nextDouble(0.0, 0.6) // 第四根柱子
            4 -> 0.5 + Random.nextDouble(0.0, 0.25) // 第五根柱子
            else -> 0.7 + Random.nextDouble(0.0, 0.6)
        }

        // 更宽的高度范围，从14dp到48dp
        val newHeightValue = 14 + (normalizedLevel * randomFactor * 32)
        targetBarHeights[i] = newHeightValue.toInt().dp
    }
}