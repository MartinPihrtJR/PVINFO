package cz.pihrtm.sopr.datatype

import java.time.LocalTime

data class SolarInfo(
    var fw: String = "0",
    var pv: String = "0",
    var src: String = "0",
    var bat: String = "0",
    var re_a: Int = 0,
    var re_s: Int = 0,
    var re_r: Int = 0,
    var led_p: Int = 0,
    var led_s: Int = 0,
    var led_b: Int = 0,
    var led_a: Int = 0,
    var led_e: Int = 0,
    var pH: MutableList<String> = mutableListOf("0"),
    var sH: MutableList<String> = mutableListOf("0"),
    var bH: MutableList<String> = mutableListOf("0"),
    var temp: String = "0",
    var lastUpdated: LocalTime = LocalTime.now()
    /*
    "hw": 1.00,
    "pv": 38.1,
    "src": 28.5,
    "bat": 26.4,
    "re_aux": true,
    "re_sol": 0,
    "re_reg": true,
    "led_pv": true,
    "led_src": true,
    "led_bat": true,
    "led_aux": true,
    "led_err": 0,
    "pv_hist": [38.1, 25.6, 28.2, 32.2],
    "src_hist": [38.1, 25.6, 28.2, 32.2],
    "bat_hist": [38.1, 25.6, 28.2, 32.2],
*/
)
