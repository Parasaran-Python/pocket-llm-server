package pyhon.pro.localhost_ai.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import pyhon.pro.localhost_ai.databinding.FragmentLogsBinding
import pyhon.pro.localhost_ai.server.ServerLogManager
import pyhon.pro.localhost_ai.ui.adapter.ServerLogsAdapter

class LogsFragment : Fragment() {

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!

    private lateinit var logsAdapter: ServerLogsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        logsAdapter = ServerLogsAdapter()
        binding.rvServerLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvServerLogs.adapter = logsAdapter

        binding.btnClearLogs.setOnClickListener {
            ServerLogManager.clearLogs()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            ServerLogManager.logs.collect { logsList ->
                logsAdapter.updateLogs(logsList)
                if (logsList.isEmpty()) {
                    binding.tvNoLogs.visibility = View.VISIBLE
                    binding.rvServerLogs.visibility = View.GONE
                } else {
                    binding.tvNoLogs.visibility = View.GONE
                    binding.rvServerLogs.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
