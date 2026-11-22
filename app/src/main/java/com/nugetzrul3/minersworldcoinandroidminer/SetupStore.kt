package com.nugetzrul3.minersworldcoinandroidminer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class MinerSetup(
    val name: String,
    val pool: String,
    val wallet: String,
    val password: String,
    val threads: Int,
    val algo: Int
)

class SetupStore(context: Context) {

    private val file = File(context.filesDir, "setups.json")

    fun load(): MutableList<MinerSetup> {
        if (!file.exists()) return mutableListOf()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                MinerSetup(
                    name = obj.getString("name"),
                    pool = obj.getString("pool"),
                    wallet = obj.getString("wallet"),
                    password = obj.optString("password", ""),
                    threads = obj.getInt("threads"),
                    algo = obj.getInt("algo")
                )
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(setups: List<MinerSetup>) {
        val array = JSONArray()
        for (setup in setups) {
            array.put(
                JSONObject()
                    .put("name", setup.name)
                    .put("pool", setup.pool)
                    .put("wallet", setup.wallet)
                    .put("password", setup.password)
                    .put("threads", setup.threads)
                    .put("algo", setup.algo)
            )
        }
        try {
            file.writeText(array.toString())
        } catch (e: IOException) {
            // Best effort - the app remains usable if storage is unavailable.
        }
    }
}
