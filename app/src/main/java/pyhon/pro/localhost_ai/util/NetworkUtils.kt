package pyhon.pro.localhost_ai.util

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    /**
     * Finds the primary active IPv4 address across Wi-Fi (wlan0), Hotspot (ap0), Ethernet (eth0), or USB (rndis0).
     */
    fun getLocalIpAddress(context: Context): String {
        try {
            // First check Wi-Fi Manager
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipInt = wifiInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }

            // Fallback: iterate network interfaces
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (!host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}

        return "127.0.0.1"
    }

    fun getEndpointUrl(context: Context, port: Int): String {
        val ip = getLocalIpAddress(context)
        return "http://$ip:$port"
    }

    fun generateClineConfig(endpoint: String, modelId: String, apiKey: String): String {
        return """
{
  "apiProvider": "openai-compatible",
  "openAiBaseUrl": "$endpoint/v1",
  "openAiApiKey": "${if (apiKey.isBlank()) "not-needed" else apiKey}",
  "openAiModelId": "$modelId"
}
        """.trimIndent()
    }

    fun generateContinueConfig(endpoint: String, modelId: String): String {
        return """
{
  "models": [
    {
      "title": "LocalHost AI ($modelId)",
      "provider": "openai",
      "model": "$modelId",
      "apiBase": "$endpoint/v1"
    }
  ]
}
        """.trimIndent()
    }

    fun generatePythonSnippet(endpoint: String, modelId: String, apiKey: String): String {
        return """
from openai import OpenAI

client = OpenAI(
    base_url="$endpoint/v1",
    api_key="${if (apiKey.isBlank()) "not-needed" else apiKey}"
)

response = client.chat.completions.create(
    model="$modelId",
    messages=[
        {"role": "system", "content": "You are an expert coder."},
        {"role": "user", "content": "Write a binary search function in Python."}
    ],
    stream=True
)

for chunk in response:
    content = chunk.choices[0].delta.content
    if content:
        print(content, end="", flush=True)
print()
        """.trimIndent()
    }

    fun generateCurlCommand(endpoint: String, modelId: String): String {
        return """curl -X POST $endpoint/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "$modelId",
    "messages": [
      {"role": "user", "content": "Hello LocalHost AI!"}
    ],
    "stream": true
  }'"""
    }
}
