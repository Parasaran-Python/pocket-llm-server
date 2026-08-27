package pyhon.pro.localhost_ai.engine

import com.google.gson.annotations.SerializedName

/**
 * Performance metrics returned by the native engine after text generation.
 */
data class GenerationResult(
    @SerializedName("prompt_tokens")
    val promptTokens: Int = 0,

    @SerializedName("completion_tokens")
    val completionTokens: Int = 0,

    @SerializedName("total_tokens")
    val totalTokens: Int = 0,

    @SerializedName("prompt_time_sec")
    val promptTimeSec: Double = 0.0,

    @SerializedName("gen_time_sec")
    val genTimeSec: Double = 0.0,

    @SerializedName("prompt_speed_tps")
    val promptSpeedTps: Double = 0.0,

    @SerializedName("gen_speed_tps")
    val genSpeedTps: Double = 0.0,

    @SerializedName("error")
    val error: String? = null
)
