package pyhon.pro.localhost_ai.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import pyhon.pro.localhost_ai.databinding.ItemModelCatalogBinding
import pyhon.pro.localhost_ai.model.DownloadState
import pyhon.pro.localhost_ai.model.ModelInfo

class ModelCatalogAdapter(
    private var models: List<ModelInfo>,
    private var activeModelPath: String? = null,
    private val onDownloadClick: (ModelInfo) -> Unit,
    private val onCancelClick: (ModelInfo) -> Unit,
    private val onLoadClick: (ModelInfo) -> Unit
) : RecyclerView.Adapter<ModelCatalogAdapter.ViewHolder>() {

    private var currentDownloadState: DownloadState = DownloadState.Idle

    fun updateModels(newModels: List<ModelInfo>, activePath: String? = activeModelPath) {
        models = newModels
        activeModelPath = activePath
        notifyDataSetChanged()
    }

    fun updateDownloadState(state: DownloadState) {
        currentDownloadState = state

        if (state is DownloadState.Downloading) {
            val index = models.indexOfFirst { it.id == state.modelId }
            if (index != -1) {
                notifyItemChanged(index, state)
                return
            }
        }
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemModelCatalogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemModelCatalogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = models.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val payload = payloads.first()
            if (payload is DownloadState.Downloading && payload.modelId == models[position].id) {
                bindProgress(holder.binding, payload, models[position])
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    private fun bindProgress(binding: ItemModelCatalogBinding, state: DownloadState.Downloading, model: ModelInfo) {
        binding.layoutDownloadProgress.visibility = View.VISIBLE
        binding.btnCatalogAction.visibility = View.GONE
        binding.progressDownload.isIndeterminate = false
        binding.progressDownload.progress = state.progressPercent.coerceIn(0, 100)

        val downloadedMb = state.downloadedBytes / (1024 * 1024)
        val totalMb = state.totalBytes / (1024 * 1024)
        val speedStr = String.format(java.util.Locale.US, "%.1f", state.speedMbPerSec)

        if (totalMb > 0) {
            binding.tvDownloadStatus.text = "Downloading: $downloadedMb MB / $totalMb MB (${state.progressPercent}%) • $speedStr MB/s"
        } else {
            binding.tvDownloadStatus.text = "Downloading: $downloadedMb MB • $speedStr MB/s"
        }
        binding.btnCancelDownload.setOnClickListener { onCancelClick(model) }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = models[position]
        val binding = holder.binding

        binding.tvCatalogName.text = model.name
        binding.tvCatalogSize.text = model.sizeFormatted
        binding.tvCatalogDesc.text = model.description
        binding.tvCatalogTags.text = "${model.quantType} • ${model.ramRequiredFormatted} • ${model.parameterCount}"

        val isThisDownloading = currentDownloadState is DownloadState.Downloading &&
                (currentDownloadState as DownloadState.Downloading).modelId == model.id

        if (isThisDownloading) {
            bindProgress(binding, currentDownloadState as DownloadState.Downloading, model)
        } else {
            binding.layoutDownloadProgress.visibility = View.GONE
            binding.btnCatalogAction.visibility = View.VISIBLE

            val isDownloaded = model.existsLocally()
            val isActive = isDownloaded && activeModelPath != null && model.localPath == activeModelPath

            if (isDownloaded) {
                if (isActive) {
                    binding.btnCatalogAction.text = "Active"
                    binding.btnCatalogAction.isEnabled = false
                } else {
                    binding.btnCatalogAction.text = "Load Model"
                    binding.btnCatalogAction.isEnabled = true
                    binding.btnCatalogAction.setOnClickListener { onLoadClick(model) }
                }
            } else {
                binding.btnCatalogAction.text = "Download"
                binding.btnCatalogAction.isEnabled = true
                binding.btnCatalogAction.setOnClickListener { onDownloadClick(model) }
            }
        }
    }
}
