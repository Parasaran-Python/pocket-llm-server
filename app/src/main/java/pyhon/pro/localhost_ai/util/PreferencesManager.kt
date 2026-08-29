package pyhon.pro.localhost_ai.util

import android.content.Context
import android.content.SharedPreferences

object PreferencesManager {
    private const val PREF_NAME = "localhost_ai_prefs"

    private const val KEY_SERVER_PORT = "server_port"
    private const val KEY_GPU_LAYERS = "gpu_layers"
    private const val KEY_CONTEXT_LENGTH = "context_length"
    private const val KEY_CPU_THREADS = "cpu_threads"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_LAST_LOADED_MODEL = "last_loaded_model"
    private const val KEY_HARDWARE_MODE = "hardware_mode"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getServerPort(context: Context): Int {
        return getPrefs(context).getInt(KEY_SERVER_PORT, 8080)
    }

    fun setServerPort(context: Context, port: Int) {
        getPrefs(context).edit().putInt(KEY_SERVER_PORT, port).apply()
    }

    fun getGpuLayers(context: Context): Int {
        return getPrefs(context).getInt(KEY_GPU_LAYERS, 99)
    }

    fun setGpuLayers(context: Context, layers: Int) {
        getPrefs(context).edit().putInt(KEY_GPU_LAYERS, layers).apply()
    }

    fun getContextLength(context: Context): Int {
        return getPrefs(context).getInt(KEY_CONTEXT_LENGTH, 8192)
    }

    fun setContextLength(context: Context, length: Int) {
        getPrefs(context).edit().putInt(KEY_CONTEXT_LENGTH, length).apply()
    }

    fun getCpuThreads(context: Context): Int {
        return getPrefs(context).getInt(KEY_CPU_THREADS, 4)
    }

    fun setCpuThreads(context: Context, threads: Int) {
        getPrefs(context).edit().putInt(KEY_CPU_THREADS, threads).apply()
    }

    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }

    fun setApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, key).apply()
    }

    fun getLastLoadedModel(context: Context): String? {
        return getPrefs(context).getString(KEY_LAST_LOADED_MODEL, null)
    }

    fun setLastLoadedModel(context: Context, path: String?) {
        getPrefs(context).edit().putString(KEY_LAST_LOADED_MODEL, path).apply()
    }

    fun getHardwareMode(context: Context): String {
        return getPrefs(context).getString(KEY_HARDWARE_MODE, "AUTO") ?: "AUTO"
    }

    fun setHardwareMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_HARDWARE_MODE, mode).apply()
    }
}
