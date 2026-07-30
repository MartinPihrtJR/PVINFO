package cz.pihrtm.sopr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.pihrtm.sopr.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentRefresh: Float,
    autoConnectEnabled: Boolean,
    currentBtChannel: Int,
    onBackClick: () -> Unit,
    onSaveClick: (refresh: Float, autoConnect: Boolean, btChannel: Int) -> Unit
) {
    var refreshText by remember { mutableStateOf(currentRefresh.toString()) }
    var autoConnect by remember { mutableStateOf(autoConnectEnabled) }
    var btChannelText by remember { mutableStateOf(currentBtChannel.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_text)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_btnBack)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.settings_update_rate),
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        OutlinedTextField(
                            value = refreshText,
                            onValueChange = { if (it.length <= 5) refreshText = it },
                            modifier = Modifier.width(100.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White
                            ),
                            singleLine = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.settings_bt_channel),
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        OutlinedTextField(
                            value = btChannelText,
                            onValueChange = { if (it.length <= 2) btChannelText = it },
                            modifier = Modifier.width(100.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White
                            ),
                            singleLine = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.settings_enable_autoconnect),
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = autoConnect,
                            onCheckedChange = { autoConnect = it }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val refresh = refreshText.toFloatOrNull() ?: 3f
                    val btChannel = btChannelText.toIntOrNull() ?: 1
                    onSaveClick(refresh, autoConnect, btChannel)
                },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.settings_btn_save), color = Color.White)
            }
        }
    }
}
