package cz.pihrtm.sopr

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import cz.pihrtm.sopr.datatype.Constants
import cz.pihrtm.sopr.datatype.SolarInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.time.LocalTime
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel : ViewModel() {

    private val BT_TIMEOUT = 10L
    private val BT_SOCKET_TIMEOUT = 20000L
    private val BT_READ_TIMEOUT = 15L

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private var btSocket: BluetoothSocket? = null
    private var pollingJob: Job? = null
    private var lastTime = System.currentTimeMillis()

    data class UiState(
        val isConnected: Boolean = false,
        val isConnecting: Boolean = false,
        val deviceName: String = "Not connected",
        val pageData: SolarInfo = SolarInfo(),
        val errorMessage: String? = null
    )

    fun onErrorMessageShown() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    @SuppressLint("MissingPermission")
    fun startConnection(context: Context, device: BluetoothDevice) {
        if (_uiState.value.isConnecting) return
        _uiState.value = _uiState.value.copy(isConnecting = true)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                withTimeout((BT_TIMEOUT * 1000L + BT_SOCKET_TIMEOUT).milliseconds) {
                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        device.createBond()
                        while (device.bondState != BluetoothDevice.BOND_BONDED) {
                            delay(100.milliseconds)
                        }
                    }

                    val uuids = device.uuids
                    val uuid = if (uuids != null && uuids.isNotEmpty()) UUID.fromString(uuids[0].uuid.toString()) else UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

                    btSocket?.close()
                    try {
                        // Attempt standard UUID connection
                        btSocket = device.createRfcommSocketToServiceRecord(uuid)
                        btSocket?.connect()
                    } catch (e: java.io.IOException) {
                        Log.w("Connect", "Standard connect failed. Trying reflection fallback...", e)
                        // Fallback: Bypass SDP and force connection to RFCOMM channel from settings
                        val prefs = context.getSharedPreferences(Constants.SHARED_PREFS_SETTINGS, Context.MODE_PRIVATE)
                        val btChannel = prefs.getInt(Constants.SETTINGS_KEY_BT_CHANNEL, 1)
                        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                        btSocket = method.invoke(device, btChannel) as BluetoothSocket
                        btSocket?.connect()
                    }

                    val outStream = btSocket?.outputStream
                    val inStream = btSocket?.inputStream
                    outStream?.write('?'.code)
                    outStream?.flush()

                    val response = readUntilChar(inStream, 'R')
                    if (response.contains("SOPR")) {
                        lastTime = System.currentTimeMillis()
                        val newPageData = _uiState.value.pageData.copy(lastUpdated = LocalTime.now())
                        
                        context.getSharedPreferences(Constants.SHARED_PREFS_SETTINGS, Context.MODE_PRIVATE).edit {
                            putString(Constants.SETTINGS_KEY_AUTOCONN_MAC, device.address)
                        }

                        _uiState.value = _uiState.value.copy(
                            isConnected = true,
                            isConnecting = false,
                            deviceName = device.name ?: "Unknown",
                            pageData = newPageData
                        )
                        startPollingLoop(context)
                    } else {
                        throw Exception("Verification failed")
                    }
                }
            } catch (e: Exception) {
                Log.e("Connect", "Failed", e)
                _uiState.value = _uiState.value.copy(
                    isConnected = false,
                    isConnecting = false,
                    errorMessage = "Connection Timeout"
                )
                disconnectDevice()
            }
        }
    }

    fun disconnectDevice() {
        pollingJob?.cancel()
        btSocket?.close()
        btSocket = null
        _uiState.value = _uiState.value.copy(
            isConnected = false,
            isConnecting = false,
            deviceName = "Not connected"
        )
    }

    private fun startPollingLoop(context: Context) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                if (_uiState.value.isConnected && btSocket?.isConnected == true) {
                    try {
                        val stream = btSocket?.inputStream
                        val outputStream = btSocket?.outputStream

                        outputStream?.write('J'.code)
                        outputStream?.flush()

                        readUntilChar(stream, '{')
                        val jsonSb = StringBuilder("{")
                        jsonSb.append(readUntilChar(stream, '}'))

                        val jsonString = jsonSb.toString()
                        if (jsonString.startsWith('{') && jsonString.endsWith('}') && !jsonString.contains("timeout")) {
                            val jsonDecoded = Gson().fromJson(jsonString, SolarInfo::class.java)
                            lastTime = System.currentTimeMillis()
                            jsonDecoded.lastUpdated = LocalTime.now()
                            _uiState.value = _uiState.value.copy(pageData = jsonDecoded)
                        } else if (System.currentTimeMillis() - lastTime > 20000) {
                            throw Exception("Poll timeout")
                        }
                    } catch (e: Exception) {
                        Log.e("Poll", "Error", e)
                        _uiState.value = _uiState.value.copy(
                            isConnected = false,
                            errorMessage = "Connection lost"
                        )
                        disconnectDevice()
                        break
                    }
                }
                val prefs = context.getSharedPreferences(Constants.SHARED_PREFS_SETTINGS, Context.MODE_PRIVATE)
                val delaySecs = try {
                    prefs.getFloat(Constants.SETTINGS_KEY_REFRESHSECS, 3f)
                } catch (e: Exception) {
                    prefs.getInt(Constants.SETTINGS_KEY_REFRESHSECS, 3).toFloat()
                }
                delay((delaySecs * 1000L).toLong().milliseconds)
            }
        }
    }

    private suspend fun readUntilChar(stream: InputStream?, target: Char): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        try {
            withTimeout((BT_READ_TIMEOUT * 1000L).milliseconds) {
                while (true) {
                    val data = stream?.read() ?: -1
                    if (data == -1) break
                    val receivedChar = data.toChar()
                    sb.append(receivedChar)
                    if (receivedChar == target) break
                }
            }
        } catch (e: Exception) {
            return@withContext "timeout"
        }
        sb.toString()
    }

    @SuppressLint("MissingPermission")
    fun checkAutoConnect(context: Context) {
        if (_uiState.value.isConnected || _uiState.value.isConnecting) return
        
        val prefs = context.getSharedPreferences(Constants.SHARED_PREFS_SETTINGS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Constants.SETTINGS_KEY_ENABLE_AUTOCONNECT, false)) {
            val devAddr = prefs.getString(Constants.SETTINGS_KEY_AUTOCONN_MAC, null)
            if (devAddr != null) {
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val device = bluetoothManager.adapter.bondedDevices.find { it.address == devAddr }
                device?.let { startConnection(context, it) }
            }
        }
    }

    override fun onCleared() {
        disconnectDevice()
    }
}
