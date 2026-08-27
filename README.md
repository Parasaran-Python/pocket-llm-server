# ⚡ Pocket LLM Server (LocalHost AI)

<p align="center">
  <b>High-Performance Local LLM Inference Engine &amp; OpenAI-Compatible LAN Server for Android</b><br>
  <i>Powered by Mobile GPU Acceleration (Vulkan / Adreno 740) + ARM KleidiAI &amp; OpenMP CPU Fallback</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform" />
  <img src="https://img.shields.io/badge/Architecture-ARM64--v8a%20%7C%20x86__64-blue.svg" alt="Architecture" />
  <img src="https://img.shields.io/badge/GPU%20Backend-Vulkan%20Compute%20%28Adreno%20740%29-brightgreen.svg" alt="GPU" />
  <img src="https://img.shields.io/badge/CPU%20Backend-ARM%20KleidiAI%20%2B%20NEON-orange.svg" alt="CPU" />
  <img src="https://img.shields.io/badge/API-OpenAI%20Compatible%20v1-purple.svg" alt="API" />
  <img src="https://img.shields.io/badge/Format-GGUF-yellow.svg" alt="GGUF" />
</p>

---

## 🚀 Overview

**Pocket LLM Server** turns your Android phone (e.g. Samsung Galaxy S23 Ultra with Snapdragon 8 Gen 2) into a dedicated, local OpenAI-compatible API server over LAN.

Now, your developer agents (**Cline, Roo Code, Continue.dev, Aider, Cursor, OpenCode**) or scripts on your PC can send chat completions directly to your phone over Wi-Fi, completely private, offline, and at zero API cost.

---

## ✨ Features

- 🏎️ **Adreno 740 GPU Acceleration:** Fast token inference using native Vulkan compute shaders with `n_gpu_layers` offloading.
- ⚡ **ARM KleidiAI + NEON CPU Fallback:** Automatic fallback to 8-core CPU with ARM KleidiAI and OpenMP if GPU memory limits are reached.
- 🌐 **OpenAI REST API Endpoints:**
  - `GET /v1/models` — List loaded models.
  - `POST /v1/chat/completions` — Streaming Server-Sent Events (`stream: true`) & JSON responses.
  - `POST /v1/completions` — Single prompt code autocomplete & FIM.
  - `GET /health` — Device, RAM, and server diagnostics.
- 🔋 **Zero-Sleep Foreground Service:** Uses `PARTIAL_WAKE_LOCK` and `WIFI_MODE_FULL_HIGH_PERF` to serve continuous requests without throttling when the screen is locked.
- 📦 **GGUF Model Downloader & Importer:** 1-tap downloads for popular coding and reasoning models + custom `.gguf` file import.
- 💬 **Interactive Playground:** In-app chat interface with live tokens/sec and TTFT (Time To First Token) metrics.
- 📊 **Real-Time Request Monitor:** Live log stream of incoming requests from coder agents on LAN.

---

## 📱 Tested Hardware

| Device | SoC | GPU / Accelerator | RAM | OS |
| :--- | :--- | :--- | :--- | :--- |
| **Samsung Galaxy S23 Ultra** | Snapdragon 8 Gen 2 for Galaxy | Qualcomm Adreno 740 (Vulkan 1.3) | 12 GB LPDDR5X | Android 14 / 15 |

---

## 🤖 Curated Models

| Model | Quant | Size | Recommended Use |
| :--- | :--- | :--- | :--- |
| **Qwen 2.5 Coder 1.5B Instruct** | `Q4_K_M` | 986 MB | Lightning-fast code generation for Cline / Roo Code |
| **Qwen 2.5 Coder 3B Instruct** | `Q4_K_M` | 2.1 GB | Complex multi-file refactoring and architecture |
| **Llama 3.2 1B Instruct** | `Q4_K_M` | 805 MB | Ultra-low latency conversation & tool calling |
| **Llama 3.2 3B Instruct** | `Q4_K_M` | 2.0 GB | General reasoning and balanced coding |
| **DeepSeek R1 Distill Qwen 1.5B** | `Q4_K_M` | 1.1 GB | Step-by-step chain-of-thought `<think>` reasoning |
| **SmolLM2 1.7B Instruct** | `Q4_K_M` | 1.0 GB | Compact, highly responsive edge model |

---

## 🛠️ Connecting Coder Agents on LAN

### 1. Cline / Roo Code (VS Code Extension)
In VS Code settings for Cline:
- **API Provider:** `OpenAI Compatible`
- **Base URL:** `http://<PHONE_IP>:8080/v1`
- **API Key:** `not-needed` (or leave blank)
- **Model ID:** `qwen2.5-coder-1.5b-instruct-q4_k_m`

### 2. Continue.dev (`config.json`)
```json
{
  "models": [
    {
      "title": "LocalHost AI (S23 Ultra)",
      "provider": "openai",
      "model": "qwen2.5-coder-1.5b-instruct-q4_k_m",
      "apiBase": "http://<PHONE_IP>:8080/v1"
    }
  ]
}
```

### 3. Python (`openai` SDK)
```python
from openai import OpenAI

client = OpenAI(
    base_url="http://192.168.1.105:8080/v1",
    api_key="not-needed"
)

response = client.chat.completions.create(
    model="qwen2.5-coder-1.5b-instruct-q4_k_m",
    messages=[
        {"role": "system", "content": "You are an expert coder."},
        {"role": "user", "content": "Write a quicksort algorithm in Python."}
    ],
    stream=True
)

for chunk in response:
    content = chunk.choices[0].delta.content
    if content:
        print(content, end="", flush=True)
print()
```

### 4. cURL Test
```bash
curl -X POST http://<PHONE_IP>:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "local-model",
    "messages": [
      {"role": "user", "content": "Hello Pocket LLM Server!"}
    ],
    "stream": true
  }'
```

---

## 🏗️ Building from Source

### Prerequisites
- Android Studio Ladybug / Jellyfish or higher
- Android SDK (API 35+)
- Android NDK (`28.2.13676358` or higher)
- CMake `3.22.1+`
- JDK 17+

### Build Debug APK
```bash
git clone --recurse-submodules https://github.com/Parasaran-Python/pocket-llm-server.git
cd pocket-llm-server
./gradlew assembleDebug
```

The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install to Device via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License
Licensed under the [MIT License](LICENSE).
