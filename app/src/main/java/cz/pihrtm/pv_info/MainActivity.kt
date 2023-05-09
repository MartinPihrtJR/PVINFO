package cz.pihrtm.pv_info

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.mikephil.charting.charts.LineChart
import com.google.gson.Gson
import cz.pihrtm.pv_info.datatype.SolarInfo

class MainActivity : AppCompatActivity() {

    private lateinit var connectionLayout: ConstraintLayout
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        connectionLayout = findViewById(R.id.connection_layout)
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

        btnConnect.setOnClickListener {
            val json = JSON_Generator().generateNewJSON()
            val gson = Gson()
            //var solarInfo = //TODO parse json from string to object and set views and graphs
        }


    }
}