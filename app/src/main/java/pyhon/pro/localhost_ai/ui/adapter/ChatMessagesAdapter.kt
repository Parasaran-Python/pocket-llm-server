package pyhon.pro.localhost_ai.ui.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import pyhon.pro.localhost_ai.R
import pyhon.pro.localhost_ai.databinding.ItemChatMessageBinding
import pyhon.pro.localhost_ai.server.ChatMessage

class ChatMessagesAdapter(
    private val messages: MutableList<ChatMessage> = mutableListOf()
) : RecyclerView.Adapter<ChatMessagesAdapter.ViewHolder>() {

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(content: String) {
        if (messages.isNotEmpty()) {
            val lastIndex = messages.size - 1
            messages[lastIndex] = messages[lastIndex].copy(content = content)
            notifyItemChanged(lastIndex)
        }
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }

    fun getMessages(): List<ChatMessage> = messages.toList()

    inner class ViewHolder(val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]
        val binding = holder.binding

        val isUser = msg.role.equals("user", ignoreCase = true)
        val context = holder.itemView.context

        if (isUser) {
            binding.layoutMessageRoot.gravity = Gravity.END
            binding.tvMessageRole.text = "You"
            binding.tvMessageRole.setTextColor(context.getColor(R.color.text_code))
            binding.layoutMessageBubble.setBackgroundResource(R.drawable.bg_message_user)
            val params = binding.layoutMessageBubble.layoutParams as LinearLayout.LayoutParams
            params.gravity = Gravity.END
            binding.layoutMessageBubble.layoutParams = params
        } else {
            binding.layoutMessageRoot.gravity = Gravity.START
            binding.tvMessageRole.text = "LocalHost AI (${msg.role.replaceFirstChar { it.uppercase() }})"
            binding.tvMessageRole.setTextColor(context.getColor(R.color.primary_cyan))
            binding.layoutMessageBubble.setBackgroundResource(R.drawable.bg_message_assistant)
            val params = binding.layoutMessageBubble.layoutParams as LinearLayout.LayoutParams
            params.gravity = Gravity.START
            binding.layoutMessageBubble.layoutParams = params
        }

        binding.tvMessageContent.text = msg.content
    }
}
