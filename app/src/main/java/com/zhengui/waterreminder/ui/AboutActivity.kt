package com.zhengui.waterreminder.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.zhengui.waterreminder.BuildConfig
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.databinding.ActivityAboutBinding
import com.zhengui.waterreminder.util.UpdateManager
import kotlinx.coroutines.launch

class AboutActivity : AppCompatActivity() {

    private var _binding: ActivityAboutBinding? = null
    private val binding get() = _binding!!
    private var pendingUpdateInfo: UpdateManager.UpdateInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"

        // 进入页面后静默检查更新
        checkUpdateSilently()
    }

    private fun checkUpdateSilently() {
        lifecycleScope.launch {
            val info = UpdateManager.checkForUpdate()
            if (info != null) {
                pendingUpdateInfo = info
                binding.updateHint.visibility = View.VISIBLE
                binding.tvUpdateHint.text = getString(R.string.update_available_hint, info.tagName)
                binding.updateHint.setOnClickListener { showUpdateDialog() }
            }
        }
    }

    private fun showUpdateDialog() {
        val info = pendingUpdateInfo ?: return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_new_version))
            .setMessage("v${info.tagName}\n\n${info.body}")
            .setPositiveButton(getString(R.string.update_now)) { _, _ ->
                lifecycleScope.launch {
                    UpdateManager.downloadAndInstall(this@AboutActivity, info)
                }
            }
            .setNegativeButton(getString(R.string.update_later), null)
            .show()
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}
