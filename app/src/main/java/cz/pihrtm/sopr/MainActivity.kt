package cz.pihrtm.sopr

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
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.gson.Gson
import cz.pihrtm.sopr.datatype.SolarInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt


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
    private val BT_TIMEOUT = 7L
    private val BT_READ_TIMEOUT = 15L

    private var isConnectedToDevice = false
    private var isConnectingToDevice = false
    private var deviceName: String = "Not connected"
    private lateinit var pvDevice: BluetoothDevice
    private lateinit var deviceUUID: UUID
    private lateinit var btSocket: BluetoothSocket
    private var isSocketConnected = false

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
    private lateinit var imgSopr: ImageView



    private lateinit var deviceFoundLauncher: ActivityResultLauncher<IntentSenderRequest>



    class CustomMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {
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

        val graphs = listOf(graphBattery, graphSolar,graphSource)

        for(graph in graphs){

            val graphTopOffset =    60f
            val graphLeftOffset =   60f
            val graphRightOffset =  60f
            val graphBottomOffset = 60f

            graph.setViewPortOffsets(graphLeftOffset, graphTopOffset, graphRightOffset, graphBottomOffset)

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
        }

        deviceFoundLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            // Handle the result here
            CoroutineScope(Dispatchers.IO).launch {
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
                        lastTime = LocalTime.now()
                        while (device.bondState != BluetoothDevice.BOND_BONDED) {
                            val timeNow = LocalTime.now()
                            if (timeNow.minusSeconds(BT_TIMEOUT).isAfter(lastTime)) {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(R.string.couldNotConnect, device.name),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                break
                            }
                        }
                        try {
                            val uuids = device.uuids
                            deviceUUID = UUID.fromString(uuids[0].uuid.toString())

                            deviceName = deviceToPair.name

                            dialog.show(supportFragmentManager, "LoadingDialog")
                            CoroutineScope(Dispatchers.IO).launch{
                                btSocket = pvDevice.createRfcommSocketToServiceRecord(deviceUUID)
                                Log.d("socket", btSocket.toString())
                                kotlin.runCatching {
                                    btSocket.connect()
                                }

                                while (!btSocket.isConnected) {}
                                isSocketConnected = true
                            }



                            Log.d("BT", "Connected")
                            isConnectingToDevice = true
                        } catch (_: Exception){
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
                } else {
                    // Result is not OK
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            R.string.youNeedToConnect,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

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
        if (!isGpsEnabled(this)) {
            showGpsEnableDialog(this)
        }


        txtFW.setOnClickListener {
            val url = "https://pihrt.com/elektronika/466-sopr-prepinac-pro-solarni-mini-elektrarnu"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            finish()

        }









        btnConnect.setOnClickListener {
            try {
                if (btSocket.isConnected){
                    btSocket.close()
                }
            }catch (e: Exception){
                Log.d("del Socket", e.toString())
            }

            if (!isConnectedToDevice) {

                deviceManager.associate(pairingRequest,
                    object : CompanionDeviceManager.Callback() {
                        // Called when a device is found. Launch the IntentSender so the user
                        // can select the device they want to pair with.
                        override fun onDeviceFound(chooserLauncher: IntentSender) {
                            val intentSenderRequest = IntentSenderRequest.Builder(chooserLauncher).setFillInIntent(null).build()
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


        }

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {



                    if (isConnectingToDevice) {

                        if (isSocketConnected) {
                            val outStream = btSocket.outputStream
                            val inStream = btSocket.inputStream
                            outStream.write('?'.code)
                            outStream.flush()
                            delay(1500)
                            val bufferString = readUntilChar(inStream, 'R') + "R"
                            val isDevice = bufferString.contains("SOPR")
                            Log.d("buffSTR", bufferString)
                            if (!isDevice || bufferString.contains("timeout")) {
                                dialog.dismiss()
                                isConnectingToDevice = false
                                runOnUiThread {
                                    try {
                                        btSocket.close()
                                    } catch (e: Exception){
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

                            }

                            Log.d("STRING", bufferString.toString())

                        }
                    } else if (isConnectedToDevice) {
                        if (isSocketConnected) {


                            val currentTime = LocalTime.now()

                            try {
                                // Read from the InputStream
                                val stream = btSocket.inputStream

                                readUntilChar(stream, '{')
                                val jsonSb = StringBuilder(10000)
                                jsonSb.append("{")
                                jsonSb.append( readUntilChar(stream, '}') )
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
                                if (jsonString.startsWith('{') && jsonString.endsWith('}')) {
                                    lastTime = currentTime
                                    dialog.dismiss()
                                    pageData.lastUpdated = currentTime
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



                                }

                                if (jsonString.startsWith('{') && jsonString.endsWith('}')) {
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
        txtFW.visibility = View.VISIBLE
        imgSopr.visibility = View.GONE
        updateFields()
    }

    private fun onDisconnected() {
        isSocketConnected = false
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
        txtBatteryTemp.text = getString(R.string.battery_temperature,   pageData.temp.dropLast(3))
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
        entries.removeLast()

        var dataset: LineDataSet = LineDataSet(entries, getString(R.string.graph_solar))
        dataset.color = getColor(R.color.white)
        dataset.lineWidth = 3f
        dataset.valueTextSize = 5f
        dataset.setDrawValues(false)

        var lineData = LineData(dataset)


        graphSolar.data = lineData
        graphSolar.invalidate()

        entries.clear()
        for ((index, data) in pageData.bH.withIndex()) {
            // turn your data into Entry objects
            entries.add(Entry(index.toFloat(), data.toFloat()))
        }
        entries.removeLast()

        dataset = LineDataSet(entries, getString(R.string.graph_battery))
        dataset.color = getColor(R.color.white)
        dataset.lineWidth = 3f
        dataset.valueTextSize = 5f
        dataset.setDrawValues(false)


        lineData = LineData(dataset)
        graphBattery.data = lineData
        graphBattery.invalidate()

        entries.clear()
        for ((index, data) in pageData.sH.withIndex()) {
            // turn your data into Entry objects
            entries.add(Entry(index.toFloat(), data.toFloat()))
        }
        entries.removeLast()

        dataset = LineDataSet(entries, getString(R.string.graph_source))
        dataset.color = getColor(R.color.white)
        dataset.lineWidth = 3f
        dataset.valueTextSize = 5f
        dataset.setDrawValues(false)

        lineData = LineData(dataset)
        graphSource.data = lineData
        graphSource.invalidate()



    }






    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_BLUETOOTH_CONNECT_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults.all {it == PackageManager.PERMISSION_GRANTED }) {
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

        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.R){
            if (!arePermissionsGranted()){
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
        try {
            var data: Int
            val lastTime = LocalTime.now()
            do {
                if (LocalTime.now().minusSeconds(BT_READ_TIMEOUT).isAfter(lastTime)) {
                    sb.clear()
                    sb.append("timeout")
                    break
                }
                data = stream!!.read()
                if (data != -1) {
                    receivedChar = data.toChar()
                    sb.append(receivedChar)
                }
            } while (data != -1 && receivedChar != target)
            return sb.toString()
        } catch (e: IOException) {
            // Error handling
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
        try{
            if (!btSocket.isConnected){
                if (isConnectedToDevice){
                    isSocketConnected = false
                    isConnectedToDevice = false
                    isConnectingToDevice = false
                    onDisconnected()
                }
            }
        }catch (e: Exception){
            if (isConnectedToDevice){
                isSocketConnected = false
                isConnectedToDevice = false
                isConnectingToDevice = false
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
                dialog.dismiss()
            }
            setNegativeButton(R.string.closeDialog) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                Toast.makeText(context, R.string.gps_content, Toast.LENGTH_LONG).show()
                finish()
            }
        }
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }





}