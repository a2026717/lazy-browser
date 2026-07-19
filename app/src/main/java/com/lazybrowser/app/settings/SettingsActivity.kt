package com.lazybrowser.app.settings

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.lazybrowser.app.R
import com.lazybrowser.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = SettingsManager(this)

        setupToolbar()
        loadSettings()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "设置"
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadSettings() {
        binding.switchJs.isChecked = settings.javaScriptEnabled
        binding.switchAdBlock.isChecked = settings.adBlockEnabled
        binding.switchDarkMode.isChecked = settings.darkMode
        binding.switchOffline.isChecked = settings.offlineMode

        val uaIndex = SettingsManager.USER_AGENTS.values.indexOf(settings.userAgent)
        if (uaIndex >= 0) {
            binding.tvUserAgent.text = SettingsManager.USER_AGENTS.keys.toList()[uaIndex]
        }

        binding.tvHomepage.text = settings.homepage
        binding.tvSearchEngine.text = settings.searchEngine.name
    }

    private fun setupListeners() {
        binding.switchJs.setOnCheckedChangeListener { _, checked ->
            settings.javaScriptEnabled = checked
        }

        binding.switchAdBlock.setOnCheckedChangeListener { _, checked ->
            settings.adBlockEnabled = checked
        }

        binding.switchDarkMode.setOnCheckedChangeListener { _, checked ->
            settings.darkMode = checked
        }

        binding.switchOffline.setOnCheckedChangeListener { _, checked ->
            settings.offlineMode = checked
        }

        binding.layoutUserAgent.setOnClickListener {
            showUserAgentPicker()
        }

        binding.layoutHomepage.setOnClickListener {
            showHomepageDialog()
        }

        binding.layoutSearchEngine.setOnClickListener {
            showSearchEnginePicker()
        }
    }

    private fun showUserAgentPicker() {
        val names = SettingsManager.USER_AGENTS.keys.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("选择 User-Agent")
            .setItems(names) { _, which ->
                val ua = SettingsManager.USER_AGENTS.values.toList()[which]
                settings.userAgent = ua
                binding.tvUserAgent.text = names[which]
            }
            .show()
    }

    private fun showHomepageDialog() {
        val input = android.widget.EditText(this).apply {
            setText(settings.homepage)
            hint = "https://"
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("设置主页")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                settings.homepage = input.text.toString()
                binding.tvHomepage.text = settings.homepage
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSearchEnginePicker() {
        val names = settings.searchEngines.map { it.name }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("默认搜索引擎")
            .setItems(names) { _, which ->
                settings.setSearchEngine(which)
                binding.tvSearchEngine.text = settings.searchEngines[which].name
            }
            .show()
    }
}
