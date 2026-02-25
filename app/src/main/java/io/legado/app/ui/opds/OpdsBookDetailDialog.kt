package io.legado.app.ui.opds

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.RadioButton
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.OpdsEntry
import io.legado.app.data.entities.OpdsSource
import io.legado.app.databinding.DialogOpdsBookDetailBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.opds.OpdsDownloader
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OPDS 书籍详情弹窗。
 * 显示书名、作者、简介、封面，列出可用下载格式，支持下载、进度显示和错误重试。
 */
object OpdsBookDetailDialog {

    fun show(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        entry: OpdsEntry,
        source: OpdsSource?
    ) {
        val binding = DialogOpdsBookDetailBinding.inflate(
            LayoutInflater.from(context)
        )

        // Populate book info
        binding.tvTitle.text = entry.title
        binding.tvAuthor.text = entry.author ?: ""
        binding.tvAuthor.isGone = entry.author.isNullOrBlank()

        if (!entry.summary.isNullOrBlank()) {
            binding.tvSummary.text = entry.summary
            binding.tvSummary.isVisible = true
        }

        // Load cover image
        binding.ivCover.load(entry.coverUrl, entry.title, entry.author)

        // Build format radio buttons
        val acquisitionLinks = entry.acquisitionLinks
        val hasFormats = acquisitionLinks.isNotEmpty()

        if (hasFormats) {
            binding.tvNoDownload.isGone = true
            acquisitionLinks.forEachIndexed { index, link ->
                val radio = RadioButton(context).apply {
                    text = link.formatName
                    id = index
                }
                binding.rgFormats.addView(radio)
            }
            // Select first format by default
            binding.rgFormats.check(0)
        } else {
            binding.tvFormatLabel.isGone = true
            binding.rgFormats.isGone = true
            binding.tvNoDownload.isVisible = true
        }

        var downloadJob: Job? = null

        val alertDialog = context.alert(title = entry.title) {
            customView { binding.root }

            if (hasFormats) {
                positiveButton("下载") {}
            }
            negativeButton("关闭") {}
        }

        if (hasFormats) {
            // Override positive button click to prevent auto-dismiss during download
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                val selectedIndex = binding.rgFormats.checkedRadioButtonId
                if (selectedIndex < 0 || selectedIndex >= acquisitionLinks.size) return@setOnClickListener
                val selectedLink = acquisitionLinks[selectedIndex]

                // Disable download button and show progress
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = false
                binding.llProgress.isVisible = true
                binding.llDownloadError.isGone = true

                downloadJob?.cancel()
                downloadJob = lifecycleOwner.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            OpdsDownloader.downloadAndImport(
                                entry = entry,
                                acquisitionLink = selectedLink,
                                username = source?.username,
                                password = source?.password
                            )
                        }
                        binding.llProgress.isGone = true
                        context.toastOnUi("下载完成：${entry.title}")
                        alertDialog.dismiss()
                    } catch (e: Exception) {
                        AppLog.put("OPDS 下载失败: ${e.message}", e)
                        binding.llProgress.isGone = true
                        binding.llDownloadError.isVisible = true
                        binding.tvDownloadError.text = e.message ?: "下载失败"
                        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = true
                    }
                }
            }

            // Retry button triggers the download again
            binding.btnRetry.setOnClickListener {
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.performClick()
            }
        }

        alertDialog.setOnDismissListener {
            downloadJob?.cancel()
        }
    }
}
