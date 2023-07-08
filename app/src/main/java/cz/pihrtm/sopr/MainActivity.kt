package cz.pihrtm.sopr

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.gson.Gson
import cz.pihrtm.sopr.datatype.Constants
import cz.pihrtm.sopr.datatype.SolarInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStream
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID


class LoadingDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(R.layout.dialog_loading)
        return builder.create()
    }
}


class MainActivity : AppCompatActivity() {

    private var SELECT_DEVICE_REQUEST_CODE = 0
    private val REQUEST_BLUETOOTH_CONNECT_PERMISSION = 136548
    private val BT_TIMEOUT = 10000
    private val BT_SOCKET_TIMEOUT = 20000
    private val BT_SOCKET_WHILE_TIMEOUT = 5000
    private val BT_READ_TIMEOUT = 15L
    private val BT_TIMEOUT_S = 10L

    private var isConnectedToDevice = false
    private var isConnectingToDevice = false
    private var isConnectingToSocket = false
    private var deviceName: String = "Not connected"
    private lateinit var pvDevice: BluetoothDevice
    private lateinit var deviceUUID: UUID
    private lateinit var btSocket: BluetoothSocket
    private var isSocketConnected = false

    private lateinit var dialog: LoadingDialogFragment


    private lateinit var pageData: SolarInfo

    private var lastTime = System.currentTimeMillis()

    private lateinit var deviceManager: CompanionDeviceManager

    private lateinit var statusLayout: ConstraintLayout
    private lateinit var graphLayout: ConstraintLayout
    private lateinit var ledLayout: ConstraintLayout
    private lateinit var txtConnection: TextView
    private lateinit var btnConnect: Button
    private lateinit var txtSolarV: TextView
    private lateinit var ledSolar: ImageView
    private lateinit var txtSourceV: TextView
    private lateinit var ledSource: ImageView
    private lateinit var ledOutput: ImageView
    private lateinit var ledError: ImageView
    private lateinit var txtBatteryV: TextView
    private lateinit var ledBattery: ImageView
    private lateinit var graphBattery: LineChart
    private lateinit var graphSource: LineChart
    private lateinit var graphSolar: LineChart
    private lateinit var txtFW: TextView
    private lateinit var txtBatteryTemp: TextView
    private lateinit var txtBatteryChargedBy: TextView
    private lateinit var txtLastUpdate: TextView
    private lateinit var imgSopr: ImageView
    private lateinit var btnSettings: ImageButton
    private lateinit var txtRuntime: TextView


    private lateinit var deviceFoundLauncher: ActivityResultLauncher<IntentSenderRequest>


    class CustomMarkerView(context: Context, layoutResource: Int) :
        MarkerView(context, layoutResource) {
        // Set the value text
        fun setValueText(value: String) {
            val tvValue: TextView = findViewById(R.id.tvValue)
            tvValue.text = value
        }
    }


    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pageData = SolarInfo()

        graphLayout = findViewById(R.id.graph_layout)
        statusLayout = findViewById(R.id.status_layout)
        ledLayout = findViewById(R.id.led_layout)
        txtConnection = findViewById(R.id.txt_connection)
        btnConnect = findViewById(R.id.btn_connect)
        txtSolarV = findViewById(R.id.txt_solar_v)
        ledSolar = findViewById(R.id.led_solar)
        ledError = findViewById(R.id.led_failure)
        ledOutput = findViewById(R.id.led_output)
        txtSourceV = findViewById(R.id.txt_source_v)
        ledSource = findViewById(R.id.led_source)
        txtBatteryV = findViewById(R.id.txt_battery_v)
        ledBattery = findViewById(R.id.led_battery)
        graphBattery = findViewById(R.id.graphBattery)
        graphSolar = findViewById(R.id.graphSolar)
        graphSource = findViewById(R.id.graphSource)
        txtFW = findViewById(R.id.txt_firmware)
        txtBatteryChargedBy = findViewById(R.id.txt_batteryChargedBy)
        txtBatteryTemp = findViewById(R.id.txt_batteryTemp)
        txtLastUpdate = findViewById(R.id.txt_lastUpdate)
        imgSopr = findViewById(R.id.img_sopr_logo)
        btnSettings = findViewById(R.id.btn_settings)
        txtRuntime = findViewById(R.id.txt_runtime)

        val graphs = listOf(graphBattery, graphSolar, graphSource)


        dialog = LoadingDialogFragment()
        dialog.isCancelable = false

        for (graph in graphs) {

            val graphTopOffset = 15f
            val graphLeftOffset = 15f
            val graphRightOffset = 15f
            val graphBottomOffset = 0f

            graph.setExtraOffsets(
                graphLeftOffset,
                graphTopOffset,
                graphRightOffset,
                graphBottomOffset
            )


            graph.setPinchZoom(true)
            graph.legend.textColor = getColor(R.color.white)
            graph.description.text = ""
            graph.xAxis.textColor = getColor(R.color.white)
            graph.axisLeft.textColor = getColor(R.color.white)
            graph.axisRight.textColor = getColor(R.color.white)
            graph.setDrawMarkers(true)
            val markerView = CustomMarkerView(this, R.layout.custom_marker_view)
            graph.marker = markerView



            graph.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e != null) {
                        val value = e.y.toString()
                        markerView.setValueText(value)
                        markerView.refreshContent(e, h)
                        markerView.visibility = View.VISIBLE
                    }
                }

                override fun onNothingSelected() {
                    markerView.visibility = View.GONE
                }
            })

            graph.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    // Return your custom label based on the provided value
                    return if ((value % 1) == 0f) {// number does not have fractional part
                        LocalTime.now().minusMinutes((9 - value.toInt()).toLong() * 30L).format(
                            DateTimeFormatter.ofPattern("HH:mm")
                        )
                    } else {
                        ""
                    }

                }
            }

        }

        deviceFoundLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                // Handle the result here
                Log.d("Connect", "Connecting started")
                val startTime = System.currentTimeMillis()
                var socketStartTime: Long? = 0
                var socketJob: Job? = null


                val job = CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (result.resultCode == Activity.RESULT_OK) {
                            // Result is OK
                            val data: Intent? = result.data
                            // The user chose to pair the app with a Bluetooth device.
                            val deviceToPair: BluetoothDevice? =
                                data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
                            deviceToPair?.let { device ->
                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(R.string.connectingTo, device.name),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                device.createBond()
                                // Maintain continuous interaction with a paired device.
                                pvDevice = device
                                lastTime = System.currentTimeMillis()
                                Log.d("Connect", "Wait for bonding")
                                while (device.bondState != BluetoothDevice.BOND_BONDED) {

                                }
                                Log.d("Connect", "Bonded")
                                try {
                                    val uuids = device.uuids
                                    deviceUUID = UUID.fromString(uuids[0].uuid.toString())
                                    val deviceAddr = device.address
                                    getSharedPreferences(
                                        Constants.SHARED_PREFS_SETTINGS,
                                        Context.MODE_PRIVATE
                                    ).edit().putString(
                                        Constants.SETTINGS_KEY_AUTOCONN_MAC, deviceAddr.toString()
                                    ).apply()

                                    deviceName = deviceToPair.name

                                    dialog.show(supportFragmentManager, "LoadingDialog")
                                    Log.d("Connect", "Start loading dialog and socket")
                                    socketStartTime = System.currentTimeMillis()
                                    socketJob = CoroutineScope(Dispatchers.IO).launch {
                                        btSocket =
                                            pvDevice.createRfcommSocketToServiceRecord(deviceUUID)
                                        Log.d("socket", btSocket.toString())
                                        try {
                                            btSocket.connect()
                                        } catch (e: Exception) {
                                            Log.d(
                                                "socket",
                                                "Error while connecting to socket, try 1 : $e"
                                            )
                                            delay(2000)
                                            try {
                                                btSocket.connect()
                                            } catch (e: Exception) {
                                                Log.d(
                                                    "socket",
                                                    "Error while connecting to socket, try 2 : $e"
                                                )
                                                delay(2000)
                                                try {
                                                    btSocket.connect()
                                                } catch (e: Exception) {
                                                    Log.d(
                                                        "socket",
                                                        "Error while connecting to socket, try 3 : $e"
                                                    )

                                                }
                                            }
                                        }
                                        Log.d("Connect", "Wait for btsocket to connect")
                                        val socketTimerStartTime = System.currentTimeMillis()
                                        var timerResult = true
                                        while (!btSocket.isConnected) {
                                            val currentSocketTime = System.currentTimeMillis()
                                            if (currentSocketTime - BT_SOCKET_WHILE_TIMEOUT > socketTimerStartTime) {
                                                timerResult = false
                                                Log.d("Connect", "socket timed out")
                                                break
                                            }
                                            Log.d(
                                                "socketConn",
                                                "connecting to socket, waiting for connection $currentSocketTime"
                                            )
                                            delay(500)
                                        }
                                        Log.d("Connect", "While loop for socket timeout ended")
                                        if (timerResult) {
                                            isSocketConnected = true
                                            isConnectingToSocket = false
                                            isConnectedToDevice = true
                                            runOnUiThread {
                                                try {
                                                    dialog.dismiss()
                                                } catch (e: Exception) {

                                                }
                                            }
                                            Log.d("Connect", "Autoconnect success")
                                        } else {
                                            Log.d("Connect", "Autoconnect failure")
                                            isSocketConnected = false
                                            isConnectingToSocket = false
                                            isConnectedToDevice = false
                                            runOnUiThread {
                                                onDisconnected()
                                            }

                                            Log.d("socket", "socket did not successfully connect")

                                            runOnUiThread {
                                                try {
                                                    dialog.dismiss()
                                                } catch (e: Exception) {

                                                }
                                                val builder = AlertDialog.Builder(this@MainActivity)

                                                builder.setMessage(R.string.conn_timeout)
                                                    .setCancelable(false)
                                                    .setPositiveButton(R.string.closeDialog) { dialog, _ ->
                                                        dialog.dismiss()
                                                    }

                                                val alert = builder.create()
                                                alert.show()
                                            }
                                        }

                                    }



                                    Log.d("BT", "Device connected, socket connecting")
                                    isConnectingToSocket = true
                                    isConnectingToDevice = true

                                } catch (_: Exception) {
                                    runOnUiThread {
                                        Toast.makeText(
                                            this@MainActivity,
                                            getString(R.string.couldNotConnect, device.name),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    try {
                                        this@MainActivity.dialog.dialog?.dismiss()
                                    } catch (e: Exception) {
                                        Log.d("Dialog", "dismiss Err: $e")
                                    }
                                }


                            }


                            // Process the data as needed
                        } else {
                            // Result is not OK
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    R.string.youNeedToConnect,
                                    Toast.LENGTH_LONG
                                ).show()
                                try {
                                    this@MainActivity.dialog.dialog?.dismiss()
                                } catch (e: Exception) {
                                    Log.d("Dialog", "dismiss Err: $e")
                                }
                            }

                        }

                    } finally {
                        if (!isConnectingToDevice) {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.conn_timeout),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                    }
                }

                CoroutineScope(Dispatchers.Default).launch {
                    while (job.isActive) {
                        val currTime = System.currentTimeMillis()
                        if (currTime - BT_TIMEOUT > startTime) {
                            job.cancel()
                            Log.d("job", "cancelling job")
                            try {
                                this@MainActivity.dialog.dialog?.dismiss()
                            } catch (e: Exception) {
                                Log.d("Dialog", "dismiss Err: $e")
                            }
                        }



                        try {
                            while (socketJob!!.isActive) {
                                if (currTime - BT_SOCKET_TIMEOUT > socketStartTime!!) {
                                    socketJob!!.cancel()
                                    Log.d("socketJob", "cancelling job")

                                    try {
                                        this@MainActivity.dialog.dialog?.dismiss()
                                    } catch (e: Exception) {
                                        Log.d("Dialog", "dismiss Err: $e")
                                    }

                                }

                            }
                        } catch (e: Exception) {
                            Log.d(
                                "socketJob",
                                "socket job not initialized, probably no device selected."
                            )

                        }
                    }

                    try {
                        while (socketJob!!.isActive) {
                            val currTime = System.currentTimeMillis()
                            if (currTime - BT_SOCKET_TIMEOUT > socketStartTime!!) {
                                socketJob!!.cancel()
                                Log.d("socketJob", "cancelling job")
                                try {
                                    this@MainActivity.dialog.dialog?.dismiss()
                                } catch (e: Exception) {
                                    Log.d("Dialog", "dismiss Err: $e")
                                }
                                runOnUiThread {
                                    try {
                                        dialog.dismiss()
                                    } catch (e: Exception) {

                                    }
                                    val builder = AlertDialog.Builder(this@MainActivity)

                                    builder.setMessage(R.string.conn_timeout)
                                        .setCancelable(false)
                                        .setPositiveButton(R.string.closeDialog) { dialog, _ ->
                                            dialog.dismiss()
                                        }

                                    val alert = builder.create()
                                    alert.show()
                                }


                            }

                        }
                    } catch (e: Exception) {
                        Log.d(
                            "socketJob",
                            "socket job not initialized, probably no device selected."
                        )
                        try {
                            this@MainActivity.dialog.dialog?.dismiss()
                        } catch (e: Exception) {
                            Log.d("Dialog", "dismiss Err: $e")
                        }
                        runOnUiThread {
                            try {
                                dialog.dismiss()
                            } catch (e: Exception) {

                            }
                            val builder = AlertDialog.Builder(this@MainActivity)

                            builder.setMessage(R.string.conn_timeout)
                                .setCancelable(false)
                                .setPositiveButton(R.string.closeDialog) { dialog, _ ->
                                    dialog.dismiss()
                                }

                            val alert = builder.create()
                            alert.show()
                        }

                    }


                }


            }







        updateFields()
        onDisconnected()

        deviceManager = getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

        val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder().build()
        val pairingRequest: AssociationRequest =
            AssociationRequest.Builder().addDeviceFilter(deviceFilter).build()


        checkForBTPerms()
        if (!isGpsEnabled(this)) {
            showGpsEnableDialog(this)
        }


        txtFW.setOnClickListener {
            val url = "https://pihrt.com/elektronika/466-sopr-prepinac-pro-solarni-mini-elektrarnu"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            finish()

        }

        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }









        btnConnect.setOnClickListener {
            try {
                if (btSocket.isConnected) {
                    btSocket.close()
                }
            } catch (e: Exception) {
                Log.d("del Socket", e.toString())
            }

            if (!isConnectedToDevice) {
                Log.d("ConnectStart", "Start")

                deviceManager.associate(
                    pairingRequest,
                    object : CompanionDeviceManager.Callback() {
                        // Called when a device is found. Launch the IntentSender so the user
                        // can select the device they want to pair with.
                        override fun onDeviceFound(chooserLauncher: IntentSender) {
                            val intentSenderRequest =
                                IntentSenderRequest.Builder(chooserLauncher).setFillInIntent(null)
                                    .build()
                            deviceFoundLauncher.launch(intentSenderRequest)
                            /*startIntentSenderForResult(
                                chooserLauncher,
                                SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0
                            )*/
                        }

                        override fun onFailure(error: CharSequence?) {
                            // Handle the failure.
                        }
                    }, null
                )
            } else {
                isConnectedToDevice = false
                onDisconnected()
                btSocket.close()
            }

            onDisconnected()
        }

        if (getSharedPreferences(Constants.SHARED_PREFS_SETTINGS, Context.MODE_PRIVATE).getBoolean(
                Constants.SETTINGS_KEY_ENABLE_AUTOCONNECT,
                false
            )
        ) {
            if (getSharedPreferences(
                    Constants.SHARED_PREFS_SETTINGS,
                    Context.MODE_PRIVATE
                ).getString(Constants.SETTINGS_KEY_AUTOCONN_MAC, null) != null
            ) {

                Log.d("Connect", "Connecting started")
                val startTime = System.currentTimeMillis()
                var socketStartTime: Long? = 0
                var socketJob: Job? = null


                val job = CoroutineScope(Dispatchers.IO).launch {
                    try {

                        // Result is OK
                        val devAddr = getSharedPreferences(
                            Constants.SHARED_PREFS_SETTINGS,
                            Context.MODE_PRIVATE
                        ).getString(Constants.SETTINGS_KEY_AUTOCONN_MAC, null)
                        val bluetoothManager: BluetoothManager =
                            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
                        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices

                        val desiredDevice: BluetoothDevice? =
                            pairedDevices?.find { it.address == devAddr }


                        try {
                            val deviceToPair: BluetoothDevice? = desiredDevice
                            deviceToPair?.let { device ->
                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(R.string.connectingTo, device.name),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                device.createBond()
                                // Maintain continuous interaction with a paired device.
                                pvDevice = device
                                lastTime = System.currentTimeMillis()
                                Log.d("Connect", "Wait for bonding")
                                while (device.bondState != BluetoothDevice.BOND_BONDED) {

                                }
                                Log.d("Connect", "Bonded")
                                try {
                                    val uuids = device.uuids
                                    deviceUUID = UUID.fromString(uuids[0].uuid.toString())
                                    val deviceAddr = device.address
                                    getSharedPreferences(
                                        Constants.SHARED_PREFS_SETTINGS,
                                        Context.MODE_PRIVATE
                                    ).edit().putString(
                                        Constants.SETTINGS_KEY_AUTOCONN_MAC, deviceAddr.toString()
                                    ).apply()

                                    deviceName = deviceToPair.name

                                    dialog.show(supportFragmentManager, "LoadingDialog")
                                    Log.d("Connect", "Start loading dialog and socket")
                                    socketStartTime = System.currentTimeMillis()
                                    socketJob = CoroutineScope(Dispatchers.IO).launch {
                                        btSocket =
                                            pvDevice.createRfcommSocketToServiceRecord(deviceUUID)
                                        Log.d("socket", btSocket.toString())
                                        try {
                                            btSocket.connect()
                                        } catch (e: Exception) {
                                            Log.d(
                                                "socket",
                                                "Error while connecting to socket, try 1 : $e"
                                            )
                                            delay(2000)
                                            try {
                                                btSocket.connect()
                                            } catch (e: Exception) {
                                                Log.d(
                                                    "socket",
                                                    "Error while connecting to socket, try 2 : $e"
                                                )
                                                delay(2000)
                                                try {
                                                    btSocket.connect()
                                                } catch (e: Exception) {
                                                    Log.d(
                                                        "socket",
                                                        "Error while connecting to socket, try 3 : $e"
                                                    )

                                                }
                                            }
                                        }
                                        Log.d("Connect", "Wait for btsocket to connect")
                                        val socketTimerStartTime = System.currentTimeMillis()
                                        var timerResult = true
                                        while (!btSocket.isConnected) {
                                            val currentSocketTime = System.currentTimeMillis()
                                            if (currentSocketTime - BT_SOCKET_WHILE_TIMEOUT > socketTimerStartTime) {
                                                timerResult = false
                                                break
                                            }
                                            Log.d(
                                                "socketConn",
                                                "connecting to socket, waiting for connection $currentSocketTime"
                                            )
                                        }
                                        Log.d("Connect", "While loop for socket timeout ended")
                                        if (timerResult) {
                                            isSocketConnected = true
                                            isConnectingToSocket = false
                                            isConnectedToDevice = true
                                            runOnUiThread {
                                                try {
                                                    dialog.dismiss()
                                                } catch (e: Exception) {

                                                }
                                            }
                                            Log.d("Connect", "Autoconnect success")
                                        } else {
                                            Log.d("Connect", "Autoconnect failure")
                                            isSocketConnected = false
                                            isConnectingToSocket = false
                                            isConnectedToDevice = false
                                            runOnUiThread {
                                                onDisconnected()
                                            }

                                            Log.d("socket", "socket did not successfully connect")

                                            runOnUiThread {
                                                try {
                                                    dialog.dismiss()
                                                } catch (e: Exception) {

                                                }
                                                val builder = AlertDialog.Builder(this@MainActivity)

                                                builder.setMessage(R.string.conn_timeout)
                                                    .setCancelable(false)
                                                    .setPositiveButton(R.string.closeDialog) { dialog, _ ->
                                                        dialog.dismiss()
                                                    }

                                                val alert = builder.create()
                                                alert.show()
                                            }
                                        }

                                    }



                                    Log.d("BT", "Device connected, socket connecting")
                                    isConnectingToSocket = true
                                    isConnectingToDevice = true

                                } catch (_: Exception) {
                                    runOnUiThread {
                                        Toast.makeText(
                                            this@MainActivity,
                                            getString(R.string.couldNotConnect, device.name),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    try {
                                        this@MainActivity.dialog.dialog?.dismiss()
                                    } catch (e: Exception) {
                                        Log.d("Dialog", "dismiss Err: $e")
                                    }
                                }


                            }

                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.couldNotConnect, desiredDevice!!.name),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            Log.d("autoconnect", e.toString())
                        }
                        // Process the data as needed


                    } finally {
                        if (!isConnectingToDevice) {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.conn_timeout),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                    }
                }

                CoroutineScope(Dispatchers.Default).launch {
                    while (job.isActive) {
                        val currTime = System.currentTimeMillis()
                        if (currTime - BT_TIMEOUT > startTime) {
                            job.cancel()
                            Log.d("job", "cancelling job")
                            try {
                                this@MainActivity.dialog.dialog?.dismiss()
                            } catch (e: Exception) {
                                Log.d("Dialog", "dismiss Err: $e")
                            }
                        }



                        try {
                            while (socketJob!!.isActive) {
                                if (currTime - BT_SOCKET_TIMEOUT > socketStartTime!!) {
                                    socketJob!!.cancel()
                                    Log.d("socketJob", "cancelling job")

                                    try {
                                        this@MainActivity.dialog.dialog?.dismiss()
                                    } catch (e: Exception) {
                                        Log.d("Dialog", "dismiss Err: $e")
                                    }
                                    runOnUiThread {
                                        try {
                                            dialog.dismiss()
                                        } catch (e: Exception) {

                                        }
                                        val builder = AlertDialog.Builder(this@MainActivity)

                                        builder.setMessage(R.string.conn_timeout)
                                            .setCancelable(false)
                                            .setPositiveButton(R.string.closeDialog) { dialog, _ ->
                                                dialog.dismiss()
                                            }

                                        val alert = builder.create()
                                        alert.show()
                                    }
                                }

                            }
                        } catch (e: Exception) {
                            Log.d(
                                "socketJob",
                                "socket job not initialized, probably no device selected."
                            )

                        }
                    }

                    try {
                        while (socketJob!!.isActive) {
                            val currTime = System.currentTimeMillis()
                            if (currTime - BT_SOCKET_TIMEOUT > socketStartTime!!) {
                                socketJob!!.cancel()
                                Log.d("socketJob", "cancelling job")
                                try {
                                    this@MainActivity.dialog.dialog?.dismiss()
                                } catch (e: Exception) {
                                    Log.d("Dialog", "dismiss Err: $e")
                                }

                                runOnUiThread {
                                    try {
                                        dialog.dismiss()
                                    } catch (e: Exception) {

                                    }
                                    val builder = AlertDialog.Builder(this@MainActivity)

                                    builder.setMessage(R.string.conn_timeout)
                                        .setCancelable(false)
                                        .setPositiveButton(R.string.closeDialog) { dialog, _ ->
                                            dialog.dismiss()
                                        }

                                    val alert = builder.create()
                                    alert.show()
                                }


                            }

                        }
                    } catch (e: Exception) {
                        Log.d(
                            "socketJob",
                            "socket job not initialized, probably no device selected."
                        )
                        try {
                            this@MainActivity.dialog.dialog?.dismiss()
                        } catch (e: Exception) {
                            Log.d("Dialog", "dismiss Err: $e")
                        }
                    }


                }

                /*
                                val startTime = System.currentTimeMillis()
                                val job = CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        // The user chose to pair the app with a Bluetooth device.
                                        val devAddr = getSharedPreferences(
                                            Constants.SHARED_PREFS_SETTINGS,
                                            Context.MODE_PRIVATE
                                        ).getString(Constants.SETTINGS_KEY_AUTOCONN_MAC, null)
                                        val deviceToPairWMac: BluetoothDevice =
                                            BluetoothAdapter.getDefaultAdapter().getRemoteDevice(devAddr)

                                        val deviceToPair: BluetoothDevice? = deviceToPairWMac
                                        deviceToPair?.let { device ->
                                            runOnUiThread {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    getString(R.string.connectingTo, device.name),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }

                                            device.createBond()
                                            // Maintain continuous interaction with a paired device.
                                            pvDevice = device
                                            lastTime = System.currentTimeMillis()
                                            while (device.bondState != BluetoothDevice.BOND_BONDED) {
                                            }
                                            try {
                                                val uuids = device.uuids
                                                deviceUUID = UUID.fromString(uuids[0].uuid.toString())
                                                val deviceAddr = device.address
                                                getSharedPreferences(
                                                    Constants.SHARED_PREFS_SETTINGS,
                                                    Context.MODE_PRIVATE
                                                ).edit().putString(
                                                    Constants.SETTINGS_KEY_AUTOCONN_MAC, deviceAddr.toString()
                                                ).apply()

                                                deviceName = deviceToPair.name

                                                dialog.show(supportFragmentManager, "LoadingDialog")
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    btSocket =
                                                        pvDevice.createRfcommSocketToServiceRecord(deviceUUID)
                                                    Log.d("socket", btSocket.toString())
                                                    kotlin.runCatching {
                                                        btSocket.connect()
                                                    }

                                                    while (!btSocket.isConnected) {
                                                    }
                                                    isSocketConnected = true
                                                }



                                                Log.d("BT", "Connected")
                                                isConnectingToDevice = true
                                            } catch (_: Exception) {
                                                runOnUiThread {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        getString(R.string.couldNotConnect, device.name),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }


                                        }


                                        // Process the data as needed

                                    } catch (e: Exception) {

                                        Log.d("autoconnect", e.toString())
                                        runOnUiThread {
                                            Toast.makeText(
                                                this@MainActivity,
                                                getString(
                                                    R.string.couldNotConnect,
                                                    getSharedPreferences(
                                                        Constants.SHARED_PREFS_SETTINGS,
                                                        Context.MODE_PRIVATE
                                                    ).getString(Constants.SETTINGS_KEY_AUTOCONN_MAC, null)
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } finally {
                                        runOnUiThread {
                                            Toast.makeText(
                                                this@MainActivity,
                                                getString(
                                                    R.string.couldNotConnect,
                                                    getSharedPreferences(
                                                        Constants.SHARED_PREFS_SETTINGS,
                                                        Context.MODE_PRIVATE
                                                    ).getString(Constants.SETTINGS_KEY_AUTOCONN_MAC, null)
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }

                                while (job.isActive) {
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - BT_TIMEOUT > startTime) {
                                        job.cancel()
                                        try {
                                            this@MainActivity.dialog.dialog?.dismiss()
                                        } catch (e: Exception) {
                                            Log.d("Dialog", "dismiss Err: $e")
                                        }
                                    }
                                }
                */

            }
        }


        CoroutineScope(Dispatchers.Default).launch {
            while (true) {


                if (isConnectingToDevice) {

                    if (isConnectingToSocket) {

                    } else if (isSocketConnected) {
                        Log.d("actionLoop", "connectingToDevice + Socket Is connected")
                        val outStream = btSocket.outputStream
                        val inStream = btSocket.inputStream
                        val startSendJob = System.currentTimeMillis()
                        val sendJob = CoroutineScope(Dispatchers.IO).launch {
                            Log.d("verify", "sending ? on another thread")
                            outStream.write('?'.code)
                            outStream.flush()
                            delay(1500)
                        }
                        while (sendJob.isActive) {
                            val curr = System.currentTimeMillis()
                            if (curr - BT_TIMEOUT > startSendJob) {
                                sendJob.cancel()
                            }

                        }
                        Log.d("verification", "sent ?")
                        val bufferString = readUntilChar(inStream, 'R')
                        val isDevice = bufferString.contains("SOPR")
                        Log.d("buffSTR + isDevice", "$bufferString --- $isDevice")
                        if (!isDevice || bufferString.contains("timeout")) {
                            try {
                                this@MainActivity.dialog.dialog?.dismiss()
                            } catch (e: Exception) {
                                Log.d("Dialog", "dismiss Err: $e")
                            }
                            isConnectingToDevice = false
                            runOnUiThread {
                                try {
                                    btSocket.close()
                                } catch (e: Exception) {
                                    Log.d("Socket ERR", e.toString())
                                }

                                onDisconnected()
                                val builder = AlertDialog.Builder(this@MainActivity)

                                builder.setMessage(R.string.conn_timeout)
                                    .setCancelable(false)
                                    .setPositiveButton(R.string.closeDialog) { dialog, _ ->
                                        dialog.dismiss()
                                    }

                                val alert = builder.create()
                                alert.show()


                            }
                        } else {
                            isConnectingToDevice = false
                            isConnectedToDevice = true
                            try {
                                this@MainActivity.dialog.dialog?.dismiss()
                            } catch (e: Exception) {
                                Log.d("Dialog", "dismiss Err: $e")
                            }
                            runOnUiThread {
                                onConnected()
                            }

                        }

                        Log.d("STRING", bufferString.toString())

                    } else {
                        isConnectingToDevice = false
                        isConnectedToDevice = false
                        try {
                            this@MainActivity.dialog.dialog?.dismiss()
                        } catch (e: Exception) {
                            Log.d("Dialog", "dismiss Err: $e")
                        }
                    }
                } else if (isConnectedToDevice) {
                    if (isSocketConnected) {

                        Log.d("actionLoop", "connectedDevice + Socket Is connected")


                        val currentTime = System.currentTimeMillis()

                        try {
                            // Read from the InputStream
                            val stream = btSocket.inputStream
                            val outputStream = btSocket.outputStream

                            outputStream.write('J'.code)
                            outputStream.flush()

                            readUntilChar(stream, '{')
                            val jsonSb = StringBuilder(10000)
                            jsonSb.append("{")
                            jsonSb.append(readUntilChar(stream, '}'))
                            jsonSb.append("}")

                            //replace everything before {
                            var indexOfDesiredChar = jsonSb.indexOf("{")

                            if (indexOfDesiredChar != -1) {
                                // Replace the portion of the string before the desired character with an empty string
                                jsonSb.replace(0, indexOfDesiredChar, "")
                            }

                            //replace everything after }, should end up with isolated JSON
                            indexOfDesiredChar = jsonSb.indexOf("}")
                            if (indexOfDesiredChar != -1) {
                                // Replace the portion of the string before the desired character with an empty string
                                jsonSb.replace(indexOfDesiredChar + 1, jsonSb.length, "")
                            }


                            val jsonString = jsonSb.toString()



                            Log.d("JSONString", jsonString)
                            var jsonDecoded = SolarInfo()
                            if (jsonString.startsWith('{') && jsonString.endsWith('}') && !jsonString.contains(
                                    "timeout"
                                )
                            ) {
                                lastTime = currentTime
                                try {
                                    this@MainActivity.dialog.dialog?.dismiss()
                                } catch (e: Exception) {
                                    Log.d("Dialog", "dismiss Err: $e")
                                }
                                pageData.lastUpdated = LocalTime.now()
                            }

                            try {
                                jsonDecoded = Gson().fromJson(jsonString, SolarInfo::class.java)

                            } catch (e: Exception) {
                                Log.d("GSON_E", e.toString())
                            }


                            Log.d("JSON", jsonDecoded.toString())

                            pageData = jsonDecoded

                            runOnUiThread {
                                val diff = currentTime - BT_TIMEOUT
                                if (diff > lastTime) {
                                    isConnectedToDevice = false
                                    btSocket.close()
                                    onDisconnected()
                                    try {
                                        this@MainActivity.dialog.dialog?.dismiss()
                                    } catch (e: Exception) {
                                        Log.d("Dialog", "dismiss Err: $e")
                                    }
                                    val builder = AlertDialog.Builder(this@MainActivity)

                                    builder.setMessage(R.string.conn_timeout)
                                        .setCancelable(false)
                                        .setPositiveButton(R.string.closeDialog) { dialog, _ ->
                                            try {
                                                this@MainActivity.dialog.dialog?.dismiss()
                                            } catch (e: Exception) {
                                                Log.d("Dialog", "dismiss Err: $e")
                                            }
                                        }

                                    val alert = builder.create()
                                    alert.show()


                                }


                            }

                            if (jsonString.startsWith('{') && jsonString.endsWith('}') && !jsonString.contains(
                                    "timeout"
                                )
                            ) {
                                runOnUiThread {
                                    onConnected()
                                }
                            }


                        } catch (e: Exception) {
                            Log.d("ERR", e.toString())
                            runOnUiThread {
                                isConnectedToDevice = false
                                btSocket.close()
                                onDisconnected()
                                try {
                                    this@MainActivity.dialog.dialog?.dismiss()
                                } catch (e: Exception) {
                                    Log.d("Dialog", "dismiss Err: $e")
                                }
                                val builder = AlertDialog.Builder(this@MainActivity)

                                builder.setMessage(R.string.conn_timeout)
                                    .setCancelable(false)
                                    .setPositiveButton(R.string.closeDialog) { dialog, _ ->
                                        try {
                                            this@MainActivity.dialog.dialog?.dismiss()
                                        } catch (e: Exception) {
                                            Log.d("Dialog", "dismiss Err: $e")
                                        }
                                    }

                                val alert = builder.create()
                                alert.show()
                            }
                            // Start the service over to restart listening mode

                        }

                    }


                } else {
                    runOnUiThread {
                        onDisconnected()
                    }
                }

                val delay = 1000 * getSharedPreferences(
                    Constants.SHARED_PREFS_SETTINGS,
                    Context.MODE_PRIVATE
                ).getInt(Constants.SETTINGS_KEY_REFRESHSECS, 3)
                delay(delay.toLong())
            }
        }


    }


    private fun onConnected() {
        ledLayout.visibility = View.VISIBLE
        graphLayout.visibility = View.VISIBLE
        statusLayout.visibility = View.VISIBLE
        txtFW.visibility = View.VISIBLE
        imgSopr.visibility = View.GONE
        updateFields()
    }

    private fun onDisconnected() {
        isSocketConnected = false
        isConnectingToSocket = false
        isConnectedToDevice = false
        isConnectingToDevice = false
        ledLayout.visibility = View.GONE
        graphLayout.visibility = View.GONE
        statusLayout.visibility = View.GONE
        txtFW.visibility = View.GONE
        imgSopr.visibility = View.VISIBLE
        updateFields()


    }


    private fun updateFields() {

        //labels
        txtConnection.text = if (isConnectedToDevice) getString(
            R.string.connected_to,
            deviceName
        ) else getString(R.string.disconnected)
        txtSolarV.text = getString(R.string.solar_voltage, pageData.pv)
        txtSourceV.text = getString(R.string.source_voltage, pageData.src)
        txtBatteryV.text = getString(R.string.battery_voltage, pageData.bat)
        txtBatteryChargedBy.text = if (pageData.re_s == 0) getString(
            R.string.battery_charger,
            getString(R.string.graph_solar)
        ) else getString(R.string.battery_charger, getString(R.string.graph_source))
        txtBatteryTemp.text = getString(R.string.battery_temperature, pageData.temp.dropLast(3))
        txtFW.text = getString(R.string.fw_version, pageData.fw)

        btnSettings.visibility = if (isConnectedToDevice) View.GONE else View.VISIBLE


        txtLastUpdate.text = getString(
            R.string.last_update,
            pageData.lastUpdated.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        )

        var runtimeData = mutableListOf<Int>(0, 0, 0, 0)

        var runtimeDataString = pageData.run.split('.')

        for (data in runtimeDataString) {
            runtimeData[runtimeDataString.indexOf(data)] = data.toInt()
        }


        try {
            txtRuntime.text = getString(
                R.string.runtime,
                runtimeData[0],
                runtimeData[1],
                runtimeData[2],
                runtimeData[3],
            )
        } catch (e: Exception) {
            Log.d("RenderError", e.toString())
        }


        //buttons
        btnConnect.text =
            if (isConnectedToDevice) getString(R.string.btn_disconnect) else getString(R.string.btn_connect)
        //leds
        if (pageData.led_a == 1) {
            ledOutput.setImageResource(R.drawable.led_green)
        } else {
            ledOutput.setImageResource(R.drawable.led_off)
        }

        if (pageData.led_b == 1) {
            ledBattery.setImageResource(R.drawable.led_green)
        } else {
            ledBattery.setImageResource(R.drawable.led_off)
        }

        if (pageData.led_s == 1) {
            ledSource.setImageResource(R.drawable.led_green)
        } else {
            ledSource.setImageResource(R.drawable.led_off)
        }
        if (pageData.led_p == 1) {
            ledSolar.setImageResource(R.drawable.led_green)
        } else {
            ledSolar.setImageResource(R.drawable.led_off)
        }

        if (pageData.led_e == 1) {
            ledError.setImageResource(R.drawable.led_red)
        } else {
            ledError.setImageResource(R.drawable.led_off)
        }


        val entriesPhoto: MutableList<Entry> = mutableListOf(Entry())
        val entriesSource: MutableList<Entry> = mutableListOf(Entry())
        val entriesBattery: MutableList<Entry> = mutableListOf(Entry())

        for ((index, data) in pageData.pH.withIndex()) {
            // turn your data into Entry objects
            entriesPhoto.add(Entry(index.toFloat(), data.toFloat()))
        }
        entriesPhoto.removeLast()
        entriesPhoto.removeFirst()

        var dataset: LineDataSet = LineDataSet(entriesPhoto, getString(R.string.graph_solar))
        dataset.color = getColor(R.color.white)
        dataset.lineWidth = 3f
        dataset.valueTextSize = 5f
        dataset.setDrawValues(false)

        var lineData = LineData(dataset)

        Log.d("line", entriesPhoto.toString())


        graphSolar.data = lineData
        graphSolar.invalidate()



        for ((index, data) in pageData.sH.withIndex()) {
            // turn your data into Entry objects
            entriesSource.add(Entry(index.toFloat(), data.toFloat()))
        }
        entriesSource.removeLast()
        entriesSource.removeFirst()

        dataset = LineDataSet(entriesSource, getString(R.string.graph_source))
        dataset.color = getColor(R.color.white)
        dataset.lineWidth = 3f
        dataset.valueTextSize = 5f
        dataset.setDrawValues(false)

        lineData = LineData(dataset)
        graphSource.data = lineData
        graphSource.invalidate()

        Log.d("line", entriesSource.toString())




        for ((index, data) in pageData.bH.withIndex()) {
            // turn your data into Entry objects
            entriesBattery.add(Entry(index.toFloat(), data.toFloat()))
        }
        entriesBattery.removeLast()
        entriesBattery.removeFirst()

        dataset = LineDataSet(entriesBattery, getString(R.string.graph_battery))
        dataset.color = getColor(R.color.white)
        dataset.lineWidth = 3f
        dataset.valueTextSize = 5f
        dataset.setDrawValues(false)


        lineData = LineData(dataset)
        graphBattery.data = lineData
        graphBattery.invalidate()

        Log.d("line", entriesBattery.toString())


    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_BLUETOOTH_CONNECT_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Permission has been granted.
                    // Perform the Bluetooth connection here.
                } else {
                    Toast.makeText(this, getString(R.string.we_need_bt), Toast.LENGTH_LONG).show()
                    finish()
                }
                return
            }

        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    private fun checkForBTPerms() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH),
                    REQUEST_BLUETOOTH_CONNECT_PERMISSION
                )
            }

        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            if (!arePermissionsGranted()) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    REQUEST_BLUETOOTH_CONNECT_PERMISSION
                )
            }
        }
        return


    }

    private fun readUntilChar(stream: InputStream?, target: Char): String {
        val sb = StringBuilder()
        var receivedChar: Char? = null
        var data: Int
        val lastTime = LocalTime.now()
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                do {
                    data = stream!!.read()
                    //Log.d("readUntil", "reading until char, start: $lastTime, data: $data")

                    if (data != -1) {
                        receivedChar = data.toChar()
                        sb.append(receivedChar)
                    }
                } while (data != -1 && receivedChar != target)
            } catch (e: Exception) {
                // Handle the IOException
                sb.clear()
                sb.append("timeout")
                this.cancel()
            }
        }
        while (job.isActive) {
            if (LocalTime.now().minusSeconds(BT_READ_TIMEOUT).isAfter(lastTime)) {
                job.cancel()
                sb.clear()
                sb.append("timeout")

            }
        }

        return sb.toString()
    }

    private fun arePermissionsGranted(): Boolean {
        // Check if the required permissions are granted
        val bluetoothPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH
        )
        val bluetoothAdminPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_ADMIN
        )
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Return true if all the required permissions are granted
        return (bluetoothPermission == PackageManager.PERMISSION_GRANTED &&
                bluetoothAdminPermission == PackageManager.PERMISSION_GRANTED &&
                fineLocationPermission == PackageManager.PERMISSION_GRANTED &&
                coarseLocationPermission == PackageManager.PERMISSION_GRANTED)
    }


    override fun onResume() {
        try {
            if (!btSocket.isConnected) {
                if (isConnectedToDevice) {
                    isSocketConnected = false
                    isConnectingToSocket = false
                    isConnectedToDevice = false
                    isConnectingToDevice = false
                    onDisconnected()
                }
            }
        } catch (e: Exception) {
            if (isConnectedToDevice) {
                isSocketConnected = false
                isConnectedToDevice = false
                isConnectingToDevice = false
                isConnectingToSocket = false
                onDisconnected()
            }
        }
        checkForBTPerms()
        if (!isGpsEnabled(this)) {
            showGpsEnableDialog(this)
        }



        super.onResume()
    }

    override fun onPause() {


        super.onPause()
    }

    override fun onDestroy() {
        try {
            if (!btSocket.isConnected) {
                if (isConnectedToDevice) {
                    isSocketConnected = false
                    isConnectingToSocket = false
                    isConnectedToDevice = false
                    isConnectingToDevice = false
                    onDisconnected()
                }
            }
        } catch (e: Exception) {
            if (isConnectedToDevice) {
                isSocketConnected = false
                isConnectedToDevice = false
                isConnectingToDevice = false
                isConnectingToSocket = false
                onDisconnected()
            }
        }
        try {
            btSocket.close()
        } catch (e: Exception) {
            Log.d("socket", "Error closing socket - probably already closed")
        }
        super.onDestroy()
    }


    // Function to check if GPS is enabled
    private fun isGpsEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    // Function to show a dialog to enable GPS
    private fun showGpsEnableDialog(context: Context) {
        val alertDialogBuilder = AlertDialog.Builder(context)
        alertDialogBuilder.apply {
            setTitle(R.string.gps_title)
            setMessage(R.string.gps_content)
            setCancelable(false)
            setPositiveButton(R.string.gps_openSettings) { dialog: DialogInterface, _: Int ->
                // Open GPS settings
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                context.startActivity(intent)
                try {
                    this@MainActivity.dialog.dialog?.dismiss()
                } catch (e: Exception) {
                    Log.d("Dialog", "dismiss Err: $e")
                }
            }
            setNegativeButton(R.string.closeDialog) { dialog: DialogInterface, _: Int ->
                try {
                    this@MainActivity.dialog.dialog?.dismiss()
                } catch (e: Exception) {
                    Log.d("Dialog", "dismiss Err: $e")
                }
                Toast.makeText(context, R.string.gps_content, Toast.LENGTH_LONG).show()
                finish()
            }
        }
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }


}