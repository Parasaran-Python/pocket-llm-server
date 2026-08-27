package pyhon.pro.localhost_ai.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import pyhon.pro.localhost_ai.R
import pyhon.pro.localhost_ai.databinding.FragmentSettingsBinding
import pyhon.pro.localhost_ai.engine.LocalAiEngine
import pyhon.pro.localhost_ai.util.PreferencesManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadCurrentSettings()
        setupSaveButton()
        loadSystemDiagnostics()
    }

    private fun loadCurrentSettings() {
        val context = requireContext()
        binding.etServerPort.setText(PreferencesManager.getServerPort(context).toString())
        binding.etContextLength.setText(PreferencesManager.getContextLength(context).toString())
        binding.etApiKey.setText(PreferencesManager.getApiKey(context))

        val mode = PreferencesManager.getHardwareMode(context)
        when (mode) {
            "GPU" -> binding.rbModeGpu.isChecked = true
            "CPU" -> binding.rbModeCpu.isChecked = true
            else -> binding.rbModeAuto.isChecked = true
        }
    }

    private fun setupSaveButton() {
        binding.btnSaveSettings.setOnClickListener {
            val context = requireContext()
            val port = binding.etServerPort.text?.toString()?.toIntOrNull() ?: 8080
            val ctxLen = binding.etContextLength.text?.toString()?.toIntOrNull() ?: 4096
            val apiKey = binding.etApiKey.text?.toString()?.trim() ?: ""

            val mode = when (binding.rgHardwareMode.checkedRadioButtonId) {
                R.id.rb_mode_gpu -> "GPU"
                R.id.rb_mode_cpu -> "CPU"
                else -> "AUTO"
            }

            PreferencesManager.setServerPort(context, port)
            PreferencesManager.setContextLength(context, ctxLen)
            PreferencesManager.setApiKey(context, apiKey)
            PreferencesManager.setHardwareMode(context, mode)

            // Adjust GPU layers based on mode
            when (mode) {
                "GPU" -> PreferencesManager.setGpuLayers(context, 99)
                "CPU" -> PreferencesManager.setGpuLayers(context, 0)
                "AUTO" -> PreferencesManager.setGpuLayers(context, 99)
            }

            Toast.makeText(context, "Settings saved! Restart server or reload model to apply changes.", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadSystemDiagnostics() {
        viewLifecycleOwner.lifecycleScope.launch {
            val info = LocalAiEngine.getSystemInfo()
            val backends = LocalAiEngine.getAvailableBackends()
            val text = buildString {
                append("Available Backends:\n")
                backends.forEach { append(" • $it\n") }
                append("\nSystem Capabilities:\n")
                append(info)
            }
            binding.tvSystemInfo.text = text
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
