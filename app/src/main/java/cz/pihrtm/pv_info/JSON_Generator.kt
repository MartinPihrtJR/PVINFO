package cz.pihrtm.pv_info

import android.util.Log
import com.google.gson.Gson
import cz.pihrtm.pv_info.datatype.SolarInfo
import kotlin.random.Random

val minFloat = 0f
val maxFloat = 20f

class JSON_Generator {



    fun generateNewJSON(): String{
        var solarInfo = SolarInfo()
        val gson = Gson()
        var list = mutableListOf<Float>()

        solarInfo.bat = minFloat + Random.nextFloat() * (maxFloat - minFloat)
        solarInfo.hw = 1.0f
        solarInfo.pv = minFloat + Random.nextFloat() * (maxFloat - minFloat)
        solarInfo.src = minFloat + Random.nextFloat() * (maxFloat - minFloat)
        solarInfo.bat_temp = minFloat + Random.nextFloat() * (maxFloat - minFloat)
        solarInfo.led_aux = Random.nextBoolean()
        solarInfo.led_bat = Random.nextBoolean()
        solarInfo.led_err = Random.nextBoolean()
        solarInfo.led_pv = Random.nextBoolean()
        solarInfo.led_src = Random.nextBoolean()
        solarInfo.re_aux = Random.nextBoolean()
        solarInfo.re_reg = if (!solarInfo.re_aux) Random.nextBoolean() else false
        solarInfo.re_sol = !solarInfo.re_aux and !solarInfo.re_reg
        for (i in 0..10){
            list.add(minFloat + Random.nextFloat() * (maxFloat - minFloat))
        }
        solarInfo.bat_hist = list
        list.clear()
        for (i in 0..10){
            list.add(minFloat + Random.nextFloat() * (maxFloat - minFloat))
        }
        solarInfo.pv_hist = list
        list.clear()
        for (i in 0..10){
            list.add(minFloat + Random.nextFloat() * (maxFloat - minFloat))
        }
        solarInfo.src_hist

        val json = gson.toJson(solarInfo)
        Log.d("json", json)
        return json

    }

}