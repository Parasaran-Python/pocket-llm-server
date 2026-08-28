package pyhon.pro.localhost_ai.ui.fragment

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pyhon.pro.localhost_ai.databinding.FragmentModelsBinding
import pyhon.pro.localhost_ai.engine.LocalAiEngine
import pyhon.pro.localhost_ai.model.DownloadState
import pyhon.pro.localhost_ai.model.ModelCatalog
import pyhon.pro.localhost_ai.model.ModelDownloader
import pyhon.pro.localhost_ai.model.ModelInfo
import pyhon.pro.localhost_ai.ui.adapter.ModelCatalogAdapter
import pyhon.pro.localhost_ai.ui.adapter.ModelInstalledAdapter
import pyhon.pro.localhost_ai.util.PreferencesManager
import java.io.File
import java.io.FileOutputStream

class ModelsFragment : Fragment() {

    private var _binding: FragmentModelsBinding? = null
    private val binding get() = _binding!!

    private lateinit var catalogAdapter: ModelCatalogAdapter
    private lateinit var installedAdapter: ModelInstalledAdapter

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importCustomGguf(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupImportButton()
        setupUnloadButton()
        observeEngineState()
        observeDownloadState()
        refreshModelsList()
    }

    private fun setupRecyclerViews() {
        catalogAdapter = ModelCatalogAdapter(
            models = ModelCatalog.getCatalogPresetsWithStatus(requireContext()),
            activeModelPath = LocalAiEngine.activeModelPath.value,
            onDownloadClick = { model -> startDownload(model) },
            onCancelClick = { ModelDownloader.cancelDownload() },
            onLoadClick = { model -> loadModelIntoMemory(model.localPath ?: "") }
        )
        binding.rvCatalogModels.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCatalogModels.adapter = catalogAdapter

        installedAdapter = ModelInstalledAdapter(
            models = emptyList(),
            activeModelPath = LocalAiEngine.activeModelPath.value,
            onLoadClick = { model -> loadModelIntoMemory(model.localPath ?: "") },
            onDeleteClick = { model -> deleteInstalledModel(model) }
        )
        binding.rvInstalledModels.layoutManager = LinearLayoutManager(requireContext())
        binding.rvInstalledModels.adapter = installedAdapter
    }

    private fun setupImportButton() {
        binding.btnImportGguf.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }
    }

    private fun setupUnloadButton() {
        binding.btnUnloadModel.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                LocalAiEngine.unloadModel()
                PreferencesManager.setLastLoadedModel(requireContext(), null)
                Toast.makeText(requireContext(), "Model unloaded from RAM", Toast.LENGTH_SHORT).show()
                refreshModelsList()
            }
        }
    }

    private fun observeEngineState() {
        viewLifecycleOwner.lifecycleScope.launch {
            LocalAiEngine.isLoaded.collect { isLoaded ->
                if (isLoaded) {
                    val name = LocalAiEngine.activeModelName.value ?: "Active Model"
                    val backend = LocalAiEngine.activeBackend.value
                    binding.tvLoadedModelTitle.text = name
                    binding.tvLoadedModelDetails.text = "Loaded in memory • Running on $backend"
                    binding.btnUnloadModel.visibility = View.VISIBLE
                } else {
                    binding.tvLoadedModelTitle.text = "No Model Loaded"
                    binding.tvLoadedModelDetails.text = "Select a model below to load into GPU/CPU RAM"
                    binding.btnUnloadModel.visibility = View.GONE
                }
                installedAdapter.updateData(
                    ModelCatalog.getInstalledModels(requireContext()),
                    LocalAiEngine.activeModelPath.value
                )
                catalogAdapter.updateModels(
                    ModelCatalog.getCatalogPresetsWithStatus(requireContext()),
                    LocalAiEngine.activeModelPath.value
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                LocalAiEngine.isLoading,
                LocalAiEngine.loadingProgress,
                LocalAiEngine.loadingModelName
            ) { isLoading, progress, modelName ->
                Triple(isLoading, progress, modelName)
            }.collect { (isLoading, progress, modelName) ->
                if (isLoading) {
                    binding.layoutModelLoadingProgress.visibility = View.VISIBLE
                    binding.progressModelLoad.progress = progress
                    binding.tvModelLoadStatus.text = "Loading ${modelName ?: "model"} into GPU VRAM ($progress%)..."
                    binding.btnUnloadModel.visibility = View.GONE
                } else {
                    binding.layoutModelLoadingProgress.visibility = View.GONE
                }
            }
        }
    }

    private fun observeDownloadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            ModelDownloader.downloadState.collect { state ->
                catalogAdapter.updateDownloadState(state)
                if (state is DownloadState.Completed) {
                    Toast.makeText(requireContext(), "Model download complete!", Toast.LENGTH_LONG).show()
                    refreshModelsList()
                } else if (state is DownloadState.Failed) {
                    Toast.makeText(requireContext(), "Download failed: ${state.error}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshModelsList() {
        val installed = ModelCatalog.getInstalledModels(requireContext())
        installedAdapter.updateData(installed, LocalAiEngine.activeModelPath.value)

        if (installed.isEmpty()) {
            binding.tvNoInstalledModels.visibility = View.VISIBLE
            binding.rvInstalledModels.visibility = View.GONE
        } else {
            binding.tvNoInstalledModels.visibility = View.GONE
            binding.rvInstalledModels.visibility = View.VISIBLE
        }

        catalogAdapter.updateModels(
            ModelCatalog.getCatalogPresetsWithStatus(requireContext()),
            LocalAiEngine.activeModelPath.value
        )
    }

    private fun startDownload(model: ModelInfo) {
        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), "Starting download: ${model.name}...", Toast.LENGTH_SHORT).show()
            ModelDownloader.downloadModel(requireContext(), model)
        }
    }

    private fun loadModelIntoMemory(modelPath: String) {
        if (modelPath.isBlank() || !File(modelPath).exists()) {
            Toast.makeText(requireContext(), "Model file not found", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val fileName = File(modelPath).name
            Toast.makeText(requireContext(), "Loading $fileName into RAM...", Toast.LENGTH_SHORT).show()

            val gpuLayers = PreferencesManager.getGpuLayers(requireContext())
            val contextLength = PreferencesManager.getContextLength(requireContext())
            val threads = PreferencesManager.getCpuThreads(requireContext())

            val result = LocalAiEngine.loadModel(
                modelPath = modelPath,
                nGpuLayers = gpuLayers,
                nCtx = contextLength,
                nThreads = threads
            )

            if (result.isSuccess) {
                PreferencesManager.setLastLoadedModel(requireContext(), modelPath)
                Toast.makeText(requireContext(), "Successfully loaded $fileName!", Toast.LENGTH_SHORT).show()
                refreshModelsList()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                Toast.makeText(requireContext(), "Failed to load model: $errorMsg", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun deleteInstalledModel(model: ModelInfo) {
        val path = model.localPath ?: return
        val file = File(path)
        if (file.exists()) {
            if (LocalAiEngine.activeModelPath.value == path) {
                viewLifecycleOwner.lifecycleScope.launch {
                    LocalAiEngine.unloadModel()
                }
            }
            file.delete()
            Toast.makeText(requireContext(), "Deleted ${model.name}", Toast.LENGTH_SHORT).show()
            refreshModelsList()
        }
    }

    private fun importCustomGguf(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = requireContext().contentResolver
                var fileName = "imported_model.gguf"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex)
                    }
                }

                val targetFile = File(ModelCatalog.getModelsDirectory(requireContext()), fileName)

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Importing $fileName...", Toast.LENGTH_SHORT).show()
                }

                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Imported $fileName successfully!", Toast.LENGTH_SHORT).show()
                    refreshModelsList()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to import model: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
