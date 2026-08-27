package pyhon.pro.localhost_ai

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pyhon.pro.localhost_ai.engine.LocalAiEngine
import pyhon.pro.localhost_ai.util.PreferencesManager
import java.io.File

class LocalHostAiApp : Application() {

    private val appScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "LocalHostAiApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Starting LocalHost AI Application...")

        // Initialize Native Engine
        LocalAiEngine.init(this)

        // Try restoring last loaded model if it exists
        val lastModelPath = PreferencesManager.getLastLoadedModel(this)
        if (lastModelPath != null && File(lastModelPath).exists()) {
            appScope.launch {
                val gpuLayers = PreferencesManager.getGpuLayers(this@LocalHostAiApp)
                val contextLen = PreferencesManager.getContextLength(this@LocalHostAiApp)
                val threads = PreferencesManager.getCpuThreads(this@LocalHostAiApp)
                Log.i(TAG, "Restoring previous model: $lastModelPath")
                LocalAiEngine.loadModel(
                    modelPath = lastModelPath,
                    nGpuLayers = gpuLayers,
                    nCtx = contextLen,
                    nThreads = threads
                )
            }
        }
    }
}
