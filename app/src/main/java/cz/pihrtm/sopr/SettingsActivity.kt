package cz.pihrtm.sopr

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import cz.pihrtm.sopr.datatype.Constants.SETTINGS_KEY_ENABLE_AUTOCONNECT
import cz.pihrtm.sopr.datatype.Constants.SETTINGS_KEY_REFRESHSECS
import cz.pihrtm.sopr.datatype.Constants.SHARED_PREFS_SETTINGS
import kotlinx.coroutines.delay
import java.lang.Exception


class SettingsActivity : AppCompatActivity() {

    lateinit var buttonBack: Button
    lateinit var buttonSave: Button
    lateinit var delaySecondsField: EditText
    lateinit var enableAutoReconnect: Switch
    val MIN_REFRESH = 3

    override fun onCreate(savedInstanceState: Bundle?) {


        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        buttonBack = findViewById(R.id.buttonBack)
        buttonSave = findViewById(R.id.buttonSave)
        delaySecondsField = findViewById(R.id.delaySecondsField)
        enableAutoReconnect = findViewById(R.id.autoreconnect_switch)

        delaySecondsField.setText(getSharedPreferences(SHARED_PREFS_SETTINGS, Context.MODE_PRIVATE).getInt(
            SETTINGS_KEY_REFRESHSECS, 3).toString())

        enableAutoReconnect.isChecked = getSharedPreferences(SHARED_PREFS_SETTINGS, Context.MODE_PRIVATE).getBoolean(
            SETTINGS_KEY_ENABLE_AUTOCONNECT, false)

        enableAutoReconnect.setOnCheckedChangeListener { buttonView, isChecked ->
            getSharedPreferences(SHARED_PREFS_SETTINGS, Context.MODE_PRIVATE).edit().putBoolean(
                SETTINGS_KEY_ENABLE_AUTOCONNECT, isChecked).apply()
        }

        buttonBack.setOnClickListener {
            finish()
        }

        buttonSave.setOnClickListener {
            try{
                val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

// Hide the keyboard
                inputMethodManager.hideSoftInputFromWindow(delaySecondsField.windowToken, 0)
            } catch (e: Exception){

            }


            if (delaySecondsField.text.toString().toInt() < MIN_REFRESH){
                delaySecondsField.setText(MIN_REFRESH.toString())



                val inflater: LayoutInflater = layoutInflater
                val layout: View = inflater.inflate(R.layout.toast_warning, findViewById(R.id.toast_root))

                val text: TextView = layout.findViewById(R.id.textViewToast)
                text.text = getString(R.string.settings_cantBeLessThan, getString(R.string.settings_update_rate), MIN_REFRESH)

                val toast = Toast(applicationContext)
                toast.duration = Toast.LENGTH_LONG
                toast.view = layout
                toast.show()

            }
            getSharedPreferences(SHARED_PREFS_SETTINGS, Context.MODE_PRIVATE).edit().putInt(SETTINGS_KEY_REFRESHSECS, delaySecondsField.text.toString().toInt()).apply()
            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()

        }



    }
}