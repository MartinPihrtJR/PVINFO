package cz.pihrtm.pv_info.datatype

data class SolarInfo(
    var hw: String = "0",
    var pv: String = "0",
    var src: String = "0",
    var bat: String = "0",
    var re_aux: Boolean = false,
    var re_sol: Boolean = false,
    var re_reg: Boolean = false,
    var led_pv: Boolean = false,
    var led_src: Boolean = false,
    var led_bat: Boolean = false,
    var led_aux: Boolean = false,
    var led_err: Boolean = false,
    var pv_hist: MutableList<String> = mutableListOf("0"),
    var src_hist: MutableList<String> = mutableListOf("0"),
    var bat_hist: MutableList<String> = mutableListOf("0"),
    var temp: String = "0"
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
