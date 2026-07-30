package cz.pihrtm.sopr

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import cz.pihrtm.sopr.datatype.Constants
import cz.pihrtm.sopr.ui.SettingsScreen
import cz.pihrtm.sopr.ui.theme.PVINFOTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(Constants.SHARED_PREFS_SETTINGS, Context.MODE_PRIVATE)
        val currentRefresh = prefs.getFloat(Constants.SETTINGS_KEY_REFRESHSECS, 3f)
        val autoConnectEnabled = prefs.getBoolean(Constants.SETTINGS_KEY_ENABLE_AUTOCONNECT, false)
        val currentBtChannel = prefs.getInt(Constants.SETTINGS_KEY_BT_CHANNEL, 1)

        setContent {
            PVINFOTheme {
                SettingsScreen(
                    currentRefresh = currentRefresh,
                    autoConnectEnabled = autoConnectEnabled,
                    currentBtChannel = currentBtChannel,
                    onBackClick = { finish() },
                    onSaveClick = { refresh, autoConnect, btChannel ->
                        if (refresh < 0.1f || refresh > 60.0f) {
                            val msg = "Update rate must be between 0.1 and 60 seconds"
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        } else {
                            prefs.edit {
                                putFloat(Constants.SETTINGS_KEY_REFRESHSECS, refresh)
                                putBoolean(Constants.SETTINGS_KEY_ENABLE_AUTOCONNECT, autoConnect)
                                putInt(Constants.SETTINGS_KEY_BT_CHANNEL, btChannel)
                            }
                            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}
