package pyhon.pro.localhost_ai.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import pyhon.pro.localhost_ai.R
import pyhon.pro.localhost_ai.databinding.ItemModelInstalledBinding
import pyhon.pro.localhost_ai.model.ModelInfo

class ModelInstalledAdapter(
    private var models: List<ModelInfo>,
    private var activeModelPath: String?,
    private val onLoadClick: (ModelInfo) -> Unit,
    private val onDeleteClick: (ModelInfo) -> Unit
) : RecyclerView.Adapter<ModelInstalledAdapter.ViewHolder>() {

    fun updateData(newModels: List<ModelInfo>, currentActivePath: String?) {
        models = newModels
        activeModelPath = currentActivePath
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemModelInstalledBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemModelInstalledBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = models.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = models[position]
        val binding = holder.binding

        binding.tvInstalledName.text = model.name
        binding.tvInstalledSize.text = model.sizeFormatted

        val isCurrentlyActive = activeModelPath != null && activeModelPath == model.localPath

        if (isCurrentlyActive) {
            binding.tvInstalledStatus.text = "ACTIVE IN RAM"
            binding.tvInstalledStatus.setTextColor(holder.itemView.context.getColor(R.color.accent_green))
            binding.btnLoadModel.isEnabled = false
            binding.btnLoadModel.text = "Loaded"
        } else {
            binding.tvInstalledStatus.text = "Ready to load (${model.quantType})"
            binding.tvInstalledStatus.setTextColor(holder.itemView.context.getColor(R.color.text_secondary))
            binding.btnLoadModel.isEnabled = true
            binding.btnLoadModel.text = "Load"
            binding.btnLoadModel.setOnClickListener { onLoadClick(model) }
        }

        binding.btnDeleteModel.setOnClickListener { onDeleteClick(model) }
    }
}
