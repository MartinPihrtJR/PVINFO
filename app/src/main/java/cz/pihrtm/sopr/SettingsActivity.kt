package cz.pihrtm.sopr

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import cz.pihrtm.sopr.datatype.Constants.SETTINGS_KEY_REFRESHSECS
import cz.pihrtm.sopr.datatype.Constants.SHARED_PREFS_SETTINGS
import kotlinx.coroutines.delay


class SettingsActivity : AppCompatActivity() {

    lateinit var buttonBack: Button
    lateinit var buttonSave: Button
    lateinit var delaySecondsField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        buttonBack = findViewById(R.id.buttonBack)
        buttonSave = findViewById(R.id.buttonSave)
        delaySecondsField = findViewById(R.id.delaySecondsField)

        delaySecondsField.setText(getSharedPreferences(SHARED_PREFS_SETTINGS, Context.MODE_PRIVATE).getInt(
            SETTINGS_KEY_REFRESHSECS, 3).toString())


        buttonBack.setOnClickListener {
            finish()
        }

        buttonSave.setOnClickListener {
            if (delaySecondsField.text.toString().toInt() < 1){
                delaySecondsField.setText("3")
            }
            getSharedPreferences(SHARED_PREFS_SETTINGS, Context.MODE_PRIVATE).edit().putInt(SETTINGS_KEY_REFRESHSECS, delaySecondsField.text.toString().toInt()).apply()
            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()

        }

    }
}