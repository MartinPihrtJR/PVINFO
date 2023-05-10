package cz.pihrtm.pv_info

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContentProviderCompat.requireContext
import com.github.mikephil.charting.charts.LineChart
import com.google.gson.Gson
import cz.pihrtm.pv_info.datatype.SolarInfo

class MainActivity : AppCompatActivity() {

    private var SELECT_DEVICE_REQUEST_CODE = 0



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
    private lateinit var txtBatteryV: TextView
    private lateinit var ledBattery: ImageView
    private lateinit var graphBattery: LineChart
    private lateinit var graphSource: LineChart
    private lateinit var graphSolar: LineChart


    private val pairingRequest: AssociationRequest = AssociationRequest.Builder()
        // Find only devices that match this request filter.
        // Stop scanning as soon as one device matching the filter is found.
        .setSingleDevice(false)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        graphLayout = findViewById(R.id.graph_layout)
        statusLayout = findViewById(R.id.status_layout)
        ledLayout = findViewById(R.id.led_layout)
        txtConnection = findViewById(R.id.txt_connection)
        btnConnect = findViewById(R.id.btn_connect)
        txtSolarV = findViewById(R.id.txt_solar_v)
        ledSolar = findViewById(R.id.led_solar)
        txtSourceV = findViewById(R.id.txt_source_v)
        ledSource = findViewById(R.id.led_source)
        txtBatteryV = findViewById(R.id.txt_battery_v)
        ledBattery = findViewById(R.id.led_battery)
        graphBattery = findViewById(R.id.graphBattery)
        graphSolar = findViewById(R.id.graphSolar)
        graphSource = findViewById(R.id.graphSource)

        onDisconnected()

        deviceManager = getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager











        btnConnect.setOnClickListener {

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


    }



    private fun onConnected(){
        ledLayout.visibility = View.VISIBLE
        graphLayout.visibility = View.VISIBLE
        statusLayout.visibility = View.VISIBLE
    }

    private fun onDisconnected(){
        ledLayout.visibility = View.GONE
        graphLayout.visibility = View.GONE
        statusLayout.visibility = View.GONE

    }

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
                    }
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }
}