package pyhon.pro.localhost_ai.ui.fragment

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pyhon.pro.localhost_ai.databinding.FragmentPlaygroundBinding
import pyhon.pro.localhost_ai.engine.LocalAiEngine
import pyhon.pro.localhost_ai.server.ChatMessage
import pyhon.pro.localhost_ai.ui.adapter.ChatMessagesAdapter

class PlaygroundFragment : Fragment() {

    private var _binding: FragmentPlaygroundBinding? = null
    private val binding get() = _binding!!

    private lateinit var messagesAdapter: ChatMessagesAdapter
    private var generationJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaygroundBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSendButton()
        setupStopButton()
        setupClearButton()
        observeEngineState()
    }

    private fun setupRecyclerView() {
        messagesAdapter = ChatMessagesAdapter()
        binding.rvChatMessages.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.rvChatMessages.adapter = messagesAdapter

        // Initial welcome message
        messagesAdapter.addMessage(
            ChatMessage(
                role = "assistant",
                content = "Welcome to LocalHost AI Playground! Ask me to write code, explain algorithms, or test model speed."
            )
        )
    }

    private fun setupSendButton() {
        binding.btnSendPrompt.setOnClickListener {
            val text = binding.etChatInput.text?.toString()?.trim() ?: ""
            if (text.isEmpty()) return@setOnClickListener

            if (!LocalAiEngine.isLoaded.value) {
                Toast.makeText(requireContext(), "Please load a model first in the Models tab", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            binding.etChatInput.setText("")
            sendMessage(text)
        }
    }

    private fun setupStopButton() {
        binding.btnStopGeneration.setOnClickListener {
            LocalAiEngine.cancelGeneration()
            generationJob?.cancel()
            setGeneratingState(false)
        }
    }

    private fun setupClearButton() {
        binding.btnClearChat.setOnClickListener {
            messagesAdapter.clear()
            messagesAdapter.addMessage(
                ChatMessage(
                    role = "assistant",
                    content = "Chat cleared. Ready for your next prompt!"
                )
            )
            binding.tvChatSpeedStat.text = "-- t/s"
        }
    }

    private fun observeEngineState() {
        viewLifecycleOwner.lifecycleScope.launch {
            LocalAiEngine.isLoaded.collect { isLoaded ->
                if (isLoaded) {
                    val name = LocalAiEngine.activeModelName.value ?: "Loaded Model"
                    val backend = LocalAiEngine.activeBackend.value
                    binding.tvChatModelIndicator.text = "$name ($backend)"
                } else {
                    binding.tvChatModelIndicator.text = "No Model Loaded (Go to Models tab)"
                }
            }
        }
    }

    private fun sendMessage(userText: String) {
        // Add user message
        messagesAdapter.addMessage(ChatMessage(role = "user", content = userText))
        binding.rvChatMessages.scrollToPosition(messagesAdapter.itemCount - 1)

        // Add empty assistant message placeholder for streaming
        messagesAdapter.addMessage(ChatMessage(role = "assistant", content = ""))
        val assistantMsgIndex = messagesAdapter.itemCount - 1
        binding.rvChatMessages.scrollToPosition(assistantMsgIndex)

        setGeneratingState(true)

        val history = messagesAdapter.getMessages().dropLast(1).map { it.role to it.content }
        val prompt = LocalAiEngine.formatChat(history, addGenerationPrompt = true)

        val startTime = SystemClock.elapsedRealtime()
        var firstTokenTime = 0L
        var tokenCount = 0
        val accumulatedText = StringBuilder()

        generationJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                LocalAiEngine.generateFlow(
                    prompt = prompt,
                    maxTokens = 2048,
                    temperature = 0.7f,
                    topP = 0.9f
                ).collect { chunk ->
                    if (tokenCount == 0) {
                        firstTokenTime = SystemClock.elapsedRealtime() - startTime
                    }
                    tokenCount++
                    accumulatedText.append(chunk)
                    messagesAdapter.updateLastMessage(accumulatedText.toString())
                    binding.rvChatMessages.scrollToPosition(messagesAdapter.itemCount - 1)

                    val elapsed = SystemClock.elapsedRealtime() - startTime
                    if (elapsed > 0) {
                        val tps = (tokenCount.toDouble() / (elapsed.toDouble() / 1000.0))
                        binding.tvChatSpeedStat.text = "%.1f t/s (TTFT: %dms)".format(tps, firstTokenTime)
                    }
                }
            } catch (e: Exception) {
                messagesAdapter.updateLastMessage("${accumulatedText}\n\n[Error: ${e.message}]")
            } finally {
                setGeneratingState(false)
            }
        }
    }

    private fun setGeneratingState(isGenerating: Boolean) {
        if (isGenerating) {
            binding.btnSendPrompt.visibility = View.GONE
            binding.btnStopGeneration.visibility = View.VISIBLE
        } else {
            binding.btnSendPrompt.visibility = View.VISIBLE
            binding.btnStopGeneration.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        generationJob?.cancel()
        _binding = null
    }
}
