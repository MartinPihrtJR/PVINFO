package cz.pihrtm.pv_info

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.companion.AssociationRequest
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
import com.github.mikephil.charting.charts.LineChart
import cz.pihrtm.pv_info.datatype.SolarInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.Exception
import java.util.UUID


class MainActivity : AppCompatActivity() {

    private var SELECT_DEVICE_REQUEST_CODE = 0
    private val REQUEST_BLUETOOTH_CONNECT_PERMISSION = 136548


    private var isConnectedToDevice = false
    private var deviceName: String = "Not connected"
    private lateinit var pvDevice: BluetoothDevice
    private lateinit var deviceUUID: UUID
    private lateinit var btSocket: BluetoothSocket


    private lateinit var pageData: SolarInfo


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



    private val pairingRequest: AssociationRequest = AssociationRequest.Builder()
        // Find only devices that match this request filter.
        // Stop scanning as soon as one device matching the filter is found.
        .setSingleDevice(false)
        .build()

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



        updateFields()
        onDisconnected()

        deviceManager = getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

        checkForBTPerms()












        btnConnect.setOnClickListener {

            if (!isConnectedToDevice){
                deviceManager.associate(pairingRequest,
                    object : CompanionDeviceManager.Callback() {
                        // Called when a device is found. Launch the IntentSender so the user
                        // can select the device they want to pair with.
                        override fun onDeviceFound(chooserLauncher: IntentSender) {
                            startIntentSenderForResult(chooserLauncher,
                                SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0)
                        }

                        override fun onFailure(error: CharSequence?) {
                            // Handle the failure.
                        }
                    }, null)
            }
            else{
                btSocket.close()
            }





        }

        CoroutineScope(Dispatchers.IO).launch {
            while (true){
                if (isConnectedToDevice){
                    if (!btSocket.isConnected ){
                        btSocket = pvDevice.createRfcommSocketToServiceRecord(deviceUUID)

                        onConnected()

                        btSocket.connect()
                    }
                    try {
                        // Read from the InputStream
                        Log.d("all", btSocket.inputStream.toString())
                        /*var buffer = ByteArray(1024)
                        var bytes = 0

                        bytes = btSocket.inputStream.read(buffer)


                        var json = buffer.decodeToString()

                        Log.d("JSON", json)*/
                        updateFields()
                    } catch (e: Exception) {
                        Log.d("ERR", e.toString())
                        // Start the service over to restart listening mode

                    }

                }

                delay(500)
            }
        }


    }



    private fun onConnected(){
        ledLayout.visibility = View.VISIBLE
        graphLayout.visibility = View.VISIBLE
        statusLayout.visibility = View.VISIBLE
        updateFields()

    }


    private fun updateFields() {

        //labels
        txtConnection.text = if (isConnectedToDevice) getString(R.string.connected_to, deviceName) else getString(R.string.disconnected)
        txtSolarV.text = getString(R.string.solar_voltage, pageData.pv)
        txtSourceV.text = getString(R.string.source_voltage, pageData.src)
        txtBatteryV.text = getString(R.string.battery_voltage, pageData.bat)
        txtBatteryChargedBy.text = if (pageData.re_sol) getString(R.string.battery_charger, getString(R.string.graph_solar)) else getString(R.string.battery_charger, getString(R.string.graph_source))
        txtBatteryTemp.text = getString(R.string.battery_temperature, pageData.temp)
        txtFW.text = getString(R.string.fw_version, pageData.hw)


        //buttons
        btnConnect.text = if (isConnectedToDevice) getString(R.string.btn_disconnect) else getString(R.string.btn_connect)

        //leds
        if (pageData.led_aux){
            ledOutput.setImageResource(R.drawable.led_green)
        }
        else{
            ledOutput.setImageResource(R.drawable.led_off)
        }

        if (pageData.led_bat){
            ledBattery.setImageResource(R.drawable.led_green)
        }
        else{
            ledBattery.setImageResource(R.drawable.led_off)
        }

        if (pageData.led_src){
            ledSource.setImageResource(R.drawable.led_green)
        }
        else{
            ledSource.setImageResource(R.drawable.led_off)
        }
        if (pageData.led_pv){
            ledSolar.setImageResource(R.drawable.led_green)
        }
        else{
            ledSolar.setImageResource(R.drawable.led_off)
        }

        if (pageData.led_err){
            ledError.setImageResource(R.drawable.led_red)
        }
        else{
            ledError.setImageResource(R.drawable.led_off)
        }

        //TODO Grafy


    }


    private fun onDisconnected(){
        ledLayout.visibility = View.GONE
        graphLayout.visibility = View.GONE
        statusLayout.visibility = View.GONE






    }


    @SuppressLint("MissingPermission")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            SELECT_DEVICE_REQUEST_CODE -> when(resultCode) {
                Activity.RESULT_OK -> {
                    // The user chose to pair the app with a Bluetooth device.
                    val deviceToPair: BluetoothDevice? =
                        data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
                    deviceToPair?.let { device ->

                        device.createBond()
                        // Maintain continuous interaction with a paired device.
                        pvDevice = device
                        while (device.bondState != BluetoothDevice.BOND_BONDED){

                        }
                        deviceName = deviceToPair!!.name
                        deviceUUID = UUID.fromString("00030000-0000-1000-8000-00805F9B34FB")

                        Log.d("BT", "Connected")


                    }


                    isConnectedToDevice = true

                }
                Activity.RESULT_CANCELED -> {
                    Toast.makeText(this, R.string.youNeedToConnect, Toast.LENGTH_LONG).show()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_BLUETOOTH_CONNECT_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission has been granted.
                    // Perform the Bluetooth connection here.
                } else {
                    Toast.makeText(this, getString(R.string.we_need_bt), Toast.LENGTH_LONG).show()
                    checkForBTPerms()
                }
                return
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    private fun checkForBTPerms(){
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

}