package app.isaaccompanion.platform

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.isaaccompanion.net.CompanionClient
import app.isaaccompanion.resources.ResourceSource
import app.isaaccompanion.resources.StringTable
import app.isaaccompanion.resources.locateResourceRoot
import app.isaaccompanion.ui.BuiltinAssets
import app.isaaccompanion.ui.GameAssets
import app.isaaccompanion.ui.ResourceAssets
import app.isaaccompanion.ui.ResourceMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class AppModel(app: Application) : AndroidViewModel(app) {
    val client = CompanionClient()

    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    private val _assets = MutableStateFlow<GameAssets>(BuiltinAssets)
    val assets: StateFlow<GameAssets> = _assets.asStateFlow()

    private val _names = MutableStateFlow(StringTable.EMPTY)
    val names: StateFlow<StringTable> = _names.asStateFlow()

    private val _resourcesConfigured = MutableStateFlow(false)
    val resourcesConfigured: StateFlow<Boolean> = _resourcesConfigured.asStateFlow()

    /** Bumped whenever more icons have been loaded, so the canvas redraws. */
    private val _iconVersion = MutableStateFlow(0)
    val iconVersion: StateFlow<Int> = _iconVersion.asStateFlow()

    init {
        client.start(viewModelScope)
        prefs.getString(KEY_RESOURCES, null)?.let { loadResources(Uri.parse(it)) }
        viewModelScope.launch(Dispatchers.IO) {
            client.items.collect { items ->
                val assets = _assets.value as? ResourceAssets ?: return@collect
                if (assets.prefetch(items.values)) _iconVersion.update { it + 1 }
            }
        }
    }

    fun setResourcesFolder(uri: Uri) {
        prefs.edit().putString(KEY_RESOURCES, uri.toString()).apply()
        loadResources(uri)
    }

    private fun loadResources(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val picked: ResourceSource = safResourceSource(getApplication(), uri)
            val root = locateResourceRoot(picked)
            if (root == null) {
                _resourcesConfigured.value = false
                return@launch
            }
            val assets = ResourceAssets.load(root, ::decodeArgb)
            val names = root.open(ResourceMap.STRING_TABLE)?.use { StringTable.parse(it, Locale.getDefault()) }
                ?: StringTable.EMPTY
            assets.prefetch(client.items.value.values)
            _names.value = names
            _assets.value = assets
            _resourcesConfigured.value = true
            _iconVersion.update { it + 1 }
        }
    }

    override fun onCleared() {
        client.stop()
    }

    private companion object {
        const val KEY_RESOURCES = "resources_tree_uri"
    }
}
