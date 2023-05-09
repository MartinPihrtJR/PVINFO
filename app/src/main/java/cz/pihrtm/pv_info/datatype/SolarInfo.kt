package cz.pihrtm.pv_info.datatype

data class SolarInfo(
    var hw: Float = 0f,
    var pv: Float = 0f,
    var src: Float = 0f,
    var bat: Float = 0f,
    var re_aux: Boolean = false,
    var re_sol: Boolean = false,
    var re_reg: Boolean = false,
    var led_pv: Boolean = false,
    var led_src: Boolean = false,
    var led_bat: Boolean = false,
    var led_aux: Boolean = false,
    var led_err: Boolean = false,
    var pv_hist: MutableList<Float> = mutableListOf(0f),
    var src_hist: MutableList<Float> = mutableListOf(0f),
    var bat_hist: MutableList<Float> = mutableListOf(0f),
    var bat_temp: Float = 0f
    /*
    "hw": 1.00,
    "pv": 38.1,
    "src": 28.5,
    "bat": 26.4,
    "re_aux": true,
    "re_sol": false,
    "re_reg": true,
    "led_pv": true,
    "led_src": true,
    "led_bat": true,
    "led_aux": true,
    "led_err": false,
    "pv_hist": [38.1, 25.6, 28.2, 32.2],
    "src_hist": [38.1, 25.6, 28.2, 32.2],
    "bat_hist": [38.1, 25.6, 28.2, 32.2],
*/
)
