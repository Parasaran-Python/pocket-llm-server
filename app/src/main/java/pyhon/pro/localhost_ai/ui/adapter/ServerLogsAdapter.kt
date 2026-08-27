package pyhon.pro.localhost_ai.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import pyhon.pro.localhost_ai.R
import pyhon.pro.localhost_ai.databinding.ItemServerLogBinding
import pyhon.pro.localhost_ai.server.ServerLogEntry

class ServerLogsAdapter(
    private var logs: List<ServerLogEntry> = emptyList()
) : RecyclerView.Adapter<ServerLogsAdapter.ViewHolder>() {

    fun updateLogs(newLogs: List<ServerLogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemServerLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemServerLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = logs.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]
        val binding = holder.binding
        val context = holder.itemView.context

        binding.tvLogMethod.text = log.method
        binding.tvLogPath.text = log.path
        binding.tvLogTime.text = log.timestamp

        if (log.statusCode == 200) {
            binding.tvLogStatus.text = "200 OK"
            binding.tvLogStatus.setTextColor(context.getColor(R.color.gpu_badge_text))
            binding.tvLogStatus.setBackgroundResource(R.drawable.bg_badge_gpu)
        } else {
            binding.tvLogStatus.text = "${log.statusCode}"
            binding.tvLogStatus.setTextColor(context.getColor(R.color.accent_red))
            binding.tvLogStatus.setBackgroundResource(R.drawable.bg_badge_cpu)
        }

        val details = buildString {
            append("${log.clientIp} • ${log.durationMs}ms")
            if (log.tokensCount > 0) {
                append(" • ${log.tokensCount} tok")
                if (log.speedTps > 0) {
                    append(" (%.1f t/s)".format(log.speedTps))
                }
            }
            if (log.isStream) {
                append(" • SSE")
            }
            if (log.error != null) {
                append(" • Error: ${log.error}")
            }
        }
        binding.tvLogDetails.text = details
    }
}
