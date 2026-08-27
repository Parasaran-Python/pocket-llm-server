package pyhon.pro.localhost_ai.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import pyhon.pro.localhost_ai.R
import pyhon.pro.localhost_ai.databinding.FragmentDashboardBinding
import pyhon.pro.localhost_ai.engine.LocalAiEngine
import pyhon.pro.localhost_ai.service.LocalServerService
import pyhon.pro.localhost_ai.util.NetworkUtils
import pyhon.pro.localhost_ai.util.PreferencesManager

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupServerToggle()
        setupCopyButtons()
        observeServerState()
        observeEngineState()
        updateMemoryStat()
    }

    private fun setupServerToggle() {
        binding.switchServer.setOnCheckedChangeListener { _, isChecked ->
            val context = requireContext()
            if (isChecked) {
                LocalServerService.start(context)
            } else {
                LocalServerService.stop(context)
            }
        }
    }

    private fun observeServerState() {
        viewLifecycleOwner.lifecycleScope.launch {
            LocalServerService.isServerRunning.collect { isRunning ->
                binding.switchServer.isChecked = isRunning
                if (isRunning) {
                    binding.tvServerStatusBadge.text = "SERVER ONLINE"
                    binding.tvServerStatusBadge.setTextColor(requireContext().getColor(R.color.gpu_badge_text))
                    binding.tvServerStatusBadge.setBackgroundResource(R.drawable.bg_badge_gpu)
                } else {
                    binding.tvServerStatusBadge.text = "SERVER OFFLINE"
                    binding.tvServerStatusBadge.setTextColor(requireContext().getColor(R.color.accent_red))
                    binding.tvServerStatusBadge.setBackgroundResource(R.drawable.bg_badge_cpu)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            LocalServerService.serverEndpoint.collect { endpoint ->
                val port = PreferencesManager.getServerPort(requireContext())
                val currentEndpoint = if (endpoint.isNotBlank()) endpoint else NetworkUtils.getEndpointUrl(requireContext(), port)
                val fullBaseUrl = "$currentEndpoint/v1"
                binding.tvEndpointUrl.text = fullBaseUrl
                updateSnippets(currentEndpoint)
            }
        }
    }

    private fun observeEngineState() {
        viewLifecycleOwner.lifecycleScope.launch {
            LocalAiEngine.isLoaded.collect { isLoaded ->
                if (isLoaded) {
                    val name = LocalAiEngine.activeModelName.value ?: "Loaded Model"
                    binding.tvActiveModelName.text = "Model: $name"
                } else {
                    binding.tvActiveModelName.text = "Model: None (Go to Models tab)"
                }
                val port = PreferencesManager.getServerPort(requireContext())
                val currentEndpoint = NetworkUtils.getEndpointUrl(requireContext(), port)
                updateSnippets(currentEndpoint)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            LocalAiEngine.activeBackend.collect { backend ->
                binding.tvBackendBadge.text = backend
            }
        }
    }

    private fun updateSnippets(endpoint: String) {
        val modelId = LocalAiEngine.activeModelName.value ?: "local-model"
        val apiKey = PreferencesManager.getApiKey(requireContext())

        val clineJson = NetworkUtils.generateClineConfig(endpoint, modelId, apiKey)
        binding.tvSnippetCline.text = clineJson

        val pythonCode = NetworkUtils.generatePythonSnippet(endpoint, modelId, apiKey)
        binding.tvSnippetPython.text = pythonCode

        val curlCode = NetworkUtils.generateCurlCommand(endpoint, modelId)
        binding.tvSnippetCurl.text = curlCode
    }

    private fun setupCopyButtons() {
        binding.btnCopyEndpoint.setOnClickListener {
            val url = binding.tvEndpointUrl.text.toString()
            copyToClipboard("LocalHost AI Endpoint", url)
        }

        binding.btnCopyCline.setOnClickListener {
            val json = binding.tvSnippetCline.text.toString()
            copyToClipboard("Cline Config", json)
        }

        binding.btnCopyPython.setOnClickListener {
            val code = binding.tvSnippetPython.text.toString()
            copyToClipboard("Python Snippet", code)
        }

        binding.btnCopyCurl.setOnClickListener {
            val curl = binding.tvSnippetCurl.text.toString()
            copyToClipboard("cURL Command", curl)
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Copied $label to clipboard!", Toast.LENGTH_SHORT).show()
    }

    private fun updateMemoryStat() {
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        binding.tvStatRam.text = "$usedMb MB"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
