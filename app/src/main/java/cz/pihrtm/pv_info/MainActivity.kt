package cz.pihrtm.pv_info

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.gson.Gson
import cz.pihrtm.pv_info.datatype.SolarInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
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

interface BluetoothSocketListener {
    fun onSocketConnected(socket: BluetoothSocket)
    fun onSocketConnectionError(error: String)
}

class MainActivity : AppCompatActivity() {

    private var SELECT_DEVICE_REQUEST_CODE = 0
    private val REQUEST_BLUETOOTH_CONNECT_PERMISSION = 136548
    private val BT_TIMEOUT = 7L
    private val BT_READ_TIMEOUT = 15L

    private var isConnectedToDevice = false
    private var isConnectingToDevice = false
    private var deviceName: String = "Not connected"
    private lateinit var pvDevice: BluetoothDevice
    private lateinit var deviceUUID: UUID
    private lateinit var btSocket: BluetoothSocket

    private lateinit var dialog: LoadingDialogFragment


    private lateinit var pageData: SolarInfo

    private var lastTime: LocalTime = LocalTime.now()

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

        val graphs = listOf(graphBattery, graphSolar, graphSource)

        for(graph in graphs){
            graph.legend.textColor = getColor(R.color.white)
            graph.description.text = "" //TODO does not work - remake
        }


        dialog = LoadingDialogFragment()
        dialog.isCancelable = false



        updateFields()
        onDisconnected()

        deviceManager = getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

        val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder().build()
        val pairingRequest: AssociationRequest =
            AssociationRequest.Builder().addDeviceFilter(deviceFilter).build()


        checkForBTPerms()












        btnConnect.setOnClickListener {

            if (!isConnectedToDevice) {
                deviceManager.associate(pairingRequest,
                    object : CompanionDeviceManager.Callback() {
                        // Called when a device is found. Launch the IntentSender so the user
                        // can select the device they want to pair with.
                        override fun onDeviceFound(chooserLauncher: IntentSender) {
                            startIntentSenderForResult(
                                chooserLauncher,
                                SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0
                            )
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


        }

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {



                    if (isConnectingToDevice) {

                        if (btSocket.isConnected) {
                            val outStream = btSocket.outputStream
                            val inStream = btSocket.inputStream
                            outStream.write('?'.code)
                            outStream.flush()
                            outStream.write('?'.code)
                            outStream.flush()
                            outStream.write('?'.code)
                            outStream.flush()
                            outStream.write('?'.code)
                            outStream.flush()
                            val bufferString = readUntilChar(inStream, 'R') + "R"
                            val isDevice = bufferString.contains("SOPR")
                            if (!isDevice || bufferString == "timeout") {
                                dialog.dismiss()
                                isConnectingToDevice = false
                                runOnUiThread {
                                    btSocket.close()
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

                            }

                            Log.d("STRING", bufferString.toString())

                        }
                    } else if (isConnectedToDevice) {
                        if (btSocket.isConnected) {


                            val currentTime = LocalTime.now()

                            try {
                                // Read from the InputStream
                                val stream = btSocket.inputStream

                                var jsonString = readUntilChar(stream, '}') + "}"
                                jsonString = jsonString.replace("SOPR", "")
                                Log.d("JSONString", jsonString)
                                var jsonDecoded = SolarInfo()
                                if (jsonString.length > 5) {
                                    lastTime = currentTime
                                    dialog.dismiss()
                                }

                                try {
                                    jsonDecoded = Gson().fromJson(jsonString, SolarInfo::class.java)

                                } catch (_: Exception) {

                                }


                                Log.d("JSON", jsonDecoded.toString())

                                pageData = jsonDecoded

                                runOnUiThread {
                                    val diff = currentTime.minusSeconds(BT_TIMEOUT)
                                    if (diff.isAfter(lastTime)) {
                                        isConnectedToDevice = false
                                        btSocket.close()
                                        onDisconnected()
                                        dialog.dismiss()
                                        val builder = AlertDialog.Builder(this@MainActivity)

                                        builder.setMessage(R.string.conn_timeout)
                                            .setCancelable(false)
                                            .setPositiveButton(R.string.closeDialog) { dialog, _ ->
                                                dialog.dismiss()
                                            }

                                        val alert = builder.create()
                                        alert.show()


                                    }


                                    pageData.lastUpdated = currentTime
                                }

                                if (jsonString.length > 5) {
                                    runOnUiThread {
                                        onConnected()
                                    }
                                }


                            } catch (e: Exception) {
                                Log.d("ERR", e.toString())
                                // Start the service over to restart listening mode

                            }

                        }


                    } else {
                        runOnUiThread {
                            onDisconnected()
                        }
                    }

                    delay(100)
                }
            }
        


    }


    private fun onConnected() {
        ledLayout.visibility = View.VISIBLE
        graphLayout.visibility = View.VISIBLE
        statusLayout.visibility = View.VISIBLE
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
        txtBatteryChargedBy.text = if (pageData.re_s == 1) getString(
            R.string.battery_charger,
            getString(R.string.graph_solar)
        ) else getString(R.string.battery_charger, getString(R.string.graph_source))
        txtBatteryTemp.text = getString(R.string.battery_temperature, pageData.temp.toFloat())
        txtFW.text = getString(R.string.fw_version, pageData.fw)




        txtLastUpdate.text = getString(
            R.string.last_update,
            pageData.lastUpdated.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        )


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


        var entries: MutableList<Entry> = mutableListOf(Entry())
        for ((index, data) in pageData.pH.withIndex()) {
            // turn your data into Entry objects
            entries.add(Entry(index.toFloat(), data.toFloat()))
        }

        var dataset: LineDataSet = LineDataSet(entries, getString(R.string.graph_solar))
        dataset.color = getColor(R.color.white)
        dataset.lineWidth = 3f
        dataset.valueTextColor = getColor(R.color.white)

        var lineData = LineData(dataset)
        graphSolar.data = lineData
        graphSolar.setVisibleYRange(0f, 50f, YAxis.AxisDependency.LEFT)
        graphSolar.invalidate()

        entries.clear()
        for ((index, data) in pageData.bH.withIndex()) {
            // turn your data into Entry objects
            entries.add(Entry(index.toFloat(), data.toFloat()))
        }

        dataset = LineDataSet(entries, getString(R.string.graph_battery))
        dataset.color = getColor(R.color.white)
        dataset.lineWidth = 5f


        lineData = LineData(dataset)
        graphBattery.data = lineData
        graphBattery.setVisibleYRange(0f, 50f, YAxis.AxisDependency.LEFT)
        graphBattery.invalidate()

        entries.clear()
        for ((index, data) in pageData.sH.withIndex()) {
            // turn your data into Entry objects
            entries.add(Entry(index.toFloat(), data.toFloat()))
        }

        dataset = LineDataSet(entries, getString(R.string.graph_source))
        dataset.color = getColor(R.color.white)
        dataset.lineWidth = 5f
        lineData = LineData(dataset)
        graphSource.data = lineData
        graphSource.invalidate()


    }


    private fun onDisconnected() {
        ledLayout.visibility = View.GONE
        graphLayout.visibility = View.GONE
        statusLayout.visibility = View.GONE
        updateFields()


    }


    @SuppressLint("MissingPermission")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            SELECT_DEVICE_REQUEST_CODE -> when (resultCode) {
                Activity.RESULT_OK -> {
                    // The user chose to pair the app with a Bluetooth device.
                    val deviceToPair: BluetoothDevice? =
                        data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
                    deviceToPair?.let { device ->
                        Toast.makeText(
                            this,
                            getString(R.string.connectingTo, device.name),
                            Toast.LENGTH_SHORT
                        ).show()
                        device.createBond()
                        // Maintain continuous interaction with a paired device.
                        pvDevice = device
                        lastTime = LocalTime.now()
                        while (device.bondState != BluetoothDevice.BOND_BONDED) {
                            val timeNow = LocalTime.now()
                            if (timeNow.minusSeconds(5).isAfter(lastTime)) {
                                Toast.makeText(
                                    this,
                                    getString(R.string.couldNotConnect, device.name),
                                    Toast.LENGTH_SHORT
                                ).show()
                                super.onActivityResult(requestCode, resultCode, data)
                            }
                        }
                        val uuids = device.uuids
                        deviceUUID = UUID.fromString(uuids[0].uuid.toString())

                        deviceName = deviceToPair.name

                        btSocket = pvDevice.createRfcommSocketToServiceRecord(deviceUUID)
                        GlobalScope.launch(Dispatchers.IO){
                                btSocket.connect() //TODO app hangs when connecting
                        }
                        while (!btSocket.isConnected) {}

                        dialog.show(supportFragmentManager, "LoadingDialog")
                        Log.d("BT", "Connected")


                    }


                    isConnectingToDevice = true
                    super.onActivityResult(requestCode, resultCode, data)

                }

                Activity.RESULT_CANCELED -> {
                    Toast.makeText(this, R.string.youNeedToConnect, Toast.LENGTH_LONG).show()
                    super.onActivityResult(requestCode, resultCode, data)
                }
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_BLUETOOTH_CONNECT_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission has been granted.
                    // Perform the Bluetooth connection here.
                } else {
                    Toast.makeText(this, getString(R.string.we_need_bt), Toast.LENGTH_LONG).show()
                    finishActivity(0)
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
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BLUETOOTH_CONNECT_PERMISSION
            )
            return
        }
    }

    private fun readUntilChar(stream: InputStream?, target: Char): String {
        val sb = StringBuilder()
        try {
            val buffer = BufferedReader(InputStreamReader(stream))
            var r: Int
            val lastTime = LocalTime.now()
            while (buffer.read().also { r = it } != -1) {
                if (LocalTime.now().minusSeconds(BT_READ_TIMEOUT).isAfter(lastTime)) {
                    sb.clear()
                    sb.append("timeout")
                    break
                }

                val c = r.toChar()
                if (c == target) break
                sb.append(c)
            }
            return sb.toString()
        } catch (e: IOException) {
            // Error handling
        }
        return sb.toString()
    }

}