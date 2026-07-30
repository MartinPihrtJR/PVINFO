package cz.pihrtm.sopr

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import cz.pihrtm.sopr.ui.MainScreen
import cz.pihrtm.sopr.ui.theme.PVINFOTheme

class MainActivity : ComponentActivity() {

    private val REQUEST_BLUETOOTH_CONNECT_PERMISSION = 136548
    private val viewModel: MainViewModel by viewModels()
    private lateinit var deviceManager: CompanionDeviceManager
    private lateinit var deviceFoundLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        deviceManager = getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

        deviceFoundLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val deviceToPair: BluetoothDevice? = result.data?.let {
                    IntentCompat.getParcelableExtra(it, CompanionDeviceManager.EXTRA_DEVICE, BluetoothDevice::class.java)
                }
                deviceToPair?.let { viewModel.startConnection(this, it) }
            } else {
                Toast.makeText(this, R.string.youNeedToConnect, Toast.LENGTH_LONG).show()
            }
        }

        setContent {
            PVINFOTheme {
                MainScreen(
                    viewModel = viewModel,
                    onConnectClick = { associateDevice() },
                    onSettingsClick = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                )
            }
        }

        checkForBTPerms()
        if (!isGpsEnabled(this)) showGpsEnableDialog(this)
        
        viewModel.checkAutoConnect(this)
    }

    private fun associateDevice() {
        val deviceFilter = BluetoothDeviceFilter.Builder().build()
        val pairingRequest = AssociationRequest.Builder().addDeviceFilter(deviceFilter).build()
        deviceManager.associate(pairingRequest, object : CompanionDeviceManager.Callback() {
            override fun onDeviceFound(chooserLauncher: IntentSender) {
                val intentSenderRequest = IntentSenderRequest.Builder(chooserLauncher).build()
                deviceFoundLauncher.launch(intentSenderRequest)
            }
            override fun onFailure(error: CharSequence?) {
                Log.e("Connect", "Association failed: $error")
            }
        }, null)
    }

    private fun checkForBTPerms() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                perms.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
                perms.add(Manifest.permission.BLUETOOTH)
                perms.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }
        if (perms.isNotEmpty()) ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQUEST_BLUETOOTH_CONNECT_PERMISSION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_BLUETOOTH_CONNECT_PERMISSION && (grantResults.isEmpty() || grantResults.any { it != PackageManager.PERMISSION_GRANTED })) {
            Toast.makeText(this, getString(R.string.we_need_bt), Toast.LENGTH_LONG).show()
            finish()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun isGpsEnabled(context: Context): Boolean = (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isProviderEnabled(LocationManager.GPS_PROVIDER)

    private fun showGpsEnableDialog(context: Context) {
        AlertDialog.Builder(context).setTitle(R.string.gps_title).setMessage(R.string.gps_content).setCancelable(false)
            .setPositiveButton(R.string.gps_openSettings) { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            .setNegativeButton(R.string.closeDialog) { _, _ -> finish() }.show()
    }
}
