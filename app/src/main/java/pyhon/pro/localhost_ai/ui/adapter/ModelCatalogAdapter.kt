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
    private val onDownloadClick: (ModelInfo) -> Unit,
    private val onCancelClick: (ModelInfo) -> Unit,
    private val onLoadClick: (ModelInfo) -> Unit
) : RecyclerView.Adapter<ModelCatalogAdapter.ViewHolder>() {

    private var currentDownloadState: DownloadState = DownloadState.Idle

    fun updateModels(newModels: List<ModelInfo>) {
        models = newModels
        notifyDataSetChanged()
    }

    fun updateDownloadState(state: DownloadState) {
        currentDownloadState = state
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemModelCatalogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemModelCatalogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = models.size

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
            val state = currentDownloadState as DownloadState.Downloading
            binding.layoutDownloadProgress.visibility = View.VISIBLE
            val downloadedMb = state.downloadedBytes / (1024 * 1024)
            val totalMb = state.totalBytes / (1024 * 1024)
            val speedStr = String.format(java.util.Locale.US, "%.1f", state.speedMbPerSec)
            binding.tvDownloadStatus.text = "Downloading: $downloadedMb MB / $totalMb MB (${state.progressPercent}%) • $speedStr MB/s"
            binding.btnCatalogAction.visibility = View.GONE
            binding.btnCancelDownload.setOnClickListener { onCancelClick(model) }
        } else {
            binding.layoutDownloadProgress.visibility = View.GONE
            binding.btnCatalogAction.visibility = View.VISIBLE

            if (model.existsLocally()) {
                binding.btnCatalogAction.text = "Load Model"
                binding.btnCatalogAction.setOnClickListener { onLoadClick(model) }
            } else {
                binding.btnCatalogAction.text = "Download"
                binding.btnCatalogAction.setOnClickListener { onDownloadClick(model) }
            }
        }
    }
}
