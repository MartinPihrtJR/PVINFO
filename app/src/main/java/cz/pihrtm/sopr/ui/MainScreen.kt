package cz.pihrtm.sopr.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import cz.pihrtm.sopr.MainViewModel
import cz.pihrtm.sopr.R
import cz.pihrtm.sopr.datatype.SolarInfo
import cz.pihrtm.sopr.ui.theme.GreenMain
import cz.pihrtm.sopr.ui.theme.PVINFOTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    MainScreenContent(
        uiState = uiState,
        onConnectClick = onConnectClick,
        onSettingsClick = onSettingsClick,
        onDisconnectClick = { viewModel.disconnectDevice() },
        onErrorMessageShown = { viewModel.onErrorMessageShown() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenContent(
    uiState: MainViewModel.UiState,
    onConnectClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onErrorMessageShown: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (!uiState.isConnected) {
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                painter = painterResource(R.drawable.icon_settings),
                                contentDescription = "Settings",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (uiState.isConnected) {
                            stringResource(R.string.connected_to, uiState.deviceName)
                        } else {
                            stringResource(R.string.disconnected)
                        },
                        color = Color.White,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (uiState.isConnected) onDisconnectClick() else onConnectClick()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = if (uiState.isConnected) stringResource(R.string.btn_disconnect) else stringResource(R.string.btn_connect),
                            color = Color.White
                        )
                    }
                }
            }

            if (uiState.isConnected) {
                InfoSection(uiState.pageData)
                GraphsSection(uiState.pageData)
            } else {
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(GreenMain)
                        .border(3.dp, GreenMain, RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.sopr),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (uiState.isConnected){
                // Footer
                Text(
                    text = stringResource(R.string.fw_version, uiState.pageData.fw),
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clickable {
                            uriHandler.openUri("https://pihrt.com/clanky/sopr-prepinac-pro-solarni-mini-elektrarnu")
                        }
                        .padding(8.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp
                )
            } else {
                Text(
                    text = stringResource(R.string.footer_text),
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clickable {
                            uriHandler.openUri("https://pihrt.com/clanky/sopr-prepinac-pro-solarni-mini-elektrarnu")
                        }
                        .padding(8.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp
                )
            }

        }
    }

    if (uiState.isConnecting) {
        if (isSystemInDarkTheme()){
            AlertDialog(
                onDismissRequest = { },
                confirmButton = { },
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = { Text(stringResource(R.string.connecting)) },
                text = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { },
                confirmButton = { },
                titleContentColor = Color.Black,
                textContentColor = Color.Black,
                title = { Text(stringResource(R.string.connecting)) },
                text = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            )
        }

    }

    uiState.errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { onErrorMessageShown() },
            confirmButton = {
                TextButton(onClick = { onErrorMessageShown() }) {
                    Text(stringResource(R.string.closeDialog))
                }
            },
            text = { Text(msg) }
        )
    }
}

@Composable
fun InfoSection(data: SolarInfo) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusRow(stringResource(R.string.solar_voltage, data.pv), data.led_p == 1)
        StatusRow(stringResource(R.string.source_voltage, data.src), data.led_s == 1)
        StatusRow(stringResource(R.string.battery_voltage, data.bat), data.led_b == 1)
        StatusRow(stringResource(R.string.output_status), data.led_a == 1)
        StatusRow(stringResource(R.string.failure_status), data.led_e == 1, isError = true)

        Spacer(modifier = Modifier.height(8.dp))

        HtmlText(stringResource(R.string.battery_charger, if (data.re_s == 0) stringResource(R.string.graph_solar) else stringResource(R.string.graph_source)))
        HtmlText(stringResource(R.string.battery_temperature, data.temp.dropLast(3)))
        HtmlText(stringResource(R.string.last_update, data.lastUpdated.format(DateTimeFormatter.ofPattern("HH:mm:ss"))))

        val runtimeData = data.run.split('.').map { it.toIntOrNull() ?: 0 }.toMutableList()
        while (runtimeData.size < 4) runtimeData.add(0)
        HtmlText(stringResource(R.string.runtime, runtimeData[0], runtimeData[1], runtimeData[2], runtimeData[3]))
    }
}

@Composable
fun StatusRow(label: String, isOn: Boolean, isError: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.White, fontSize = 18.sp)
        Image(
            painter = painterResource(
                when {
                    !isOn -> R.drawable.led_off
                    isError -> R.drawable.led_red
                    else -> R.drawable.led_green
                }
            ),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun HtmlText(html: String) {
    val annotatedString = remember(html) {
        HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    }
    Text(
        text = annotatedString,
        color = Color.White,
        fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.End
    )
}

@Composable
fun GraphsSection(data: SolarInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        VicoChart(stringResource(R.string.graph_solar), data.pH, data.lastUpdated)
        VicoChart(stringResource(R.string.graph_source), data.sH, data.lastUpdated)
        VicoChart(stringResource(R.string.graph_battery), data.bH, data.lastUpdated)
    }
}

@Composable
fun VicoChart(label: String, data: List<String>, lastUpdated: LocalTime) {
    val modelProducer = remember { CartesianChartModelProducer() }
    
    LaunchedEffect(data) {
        val series = data.mapNotNull { it.toFloatOrNull() }
        if (series.isNotEmpty()) {
            modelProducer.runTransaction { lineModel { series(series) } }
        }
    }

    val primaryColor = Color.White

    Column {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(primaryColor)),
                            pointProvider = LineCartesianLayer.PointProvider.single(
                                LineCartesianLayer.Point(
                                    rememberShapeComponent(
                                        fill = Fill(primaryColor),
                                        shape = CircleShape
                                    ),
                                    size = 10.dp
                                )
                            ),
                            interpolator = LineCartesianLayer.Interpolator.cubic()
                        )
                    )
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = rememberAxisLabelComponent(style = TextStyle(color = Color.White))
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = rememberAxisLabelComponent(style = TextStyle(color = Color.White)),
                    valueFormatter = { _, value, _ ->
                        val minutesAgo = (data.size - 1 - value.roundToInt()) * 30
                        lastUpdated.minusMinutes(minutesAgo.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
                    },
                    itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 2 }) }
                )
            ),
            modelProducer = modelProducer,
            zoomState = rememberVicoZoomState(initialZoom = Zoom.Content),
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenConnectedPreview() {
    val sampleData = SolarInfo(
        fw = "1.23",
        run = "1.2.3.4",
        pv = "42.5",
        src = "230",
        bat = "26.8",
        led_p = 1,
        led_s = 1,
        led_b = 1,
        led_a = 1,
        led_e = 0,
        temp = "25.5 °C",
        pH = mutableListOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
        sH = mutableListOf("0", "5", "15", "25", "35", "45", "55", "65", "75", "85", "95"),
        bH = mutableListOf("0", "26.1", "26.5", "26.8", "26.7", "26.9", "27.0", "27.1", "27.2", "27.3", "27.4"),
        lastUpdated = LocalTime.now()
    )
    PVINFOTheme {
        MainScreenContent(
            uiState = MainViewModel.UiState(
                isConnected = true,
                deviceName = "SOPR Device",
                pageData = sampleData
            ),
            onConnectClick = {},
            onSettingsClick = {},
            onDisconnectClick = {},
            onErrorMessageShown = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenDisconnectedPreview() {
    PVINFOTheme {
        MainScreenContent(
            uiState = MainViewModel.UiState(
                isConnected = false
            ),
            onConnectClick = {},
            onSettingsClick = {},
            onDisconnectClick = {},
            onErrorMessageShown = {}
        )
    }
}
