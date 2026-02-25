package io.legado.app.ui.opds

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.OpdsSource
import io.legado.app.databinding.DialogOpdsSourceEditBinding
import io.legado.app.databinding.FragmentOpdsSourceBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.opds.OpdsParser
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * OPDS 源管理 Fragment
 * 展示 OPDS 源列表，支持添加、编辑、删除操作
 */
class OpdsSourceFragment : BaseFragment(R.layout.fragment_opds_source),
    OpdsSourceAdapter.Callback {

    private val binding by viewBinding(FragmentOpdsSourceBinding::bind)
    private val adapter by lazy { OpdsSourceAdapter(requireContext(), this) }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initRecyclerView()
        initFab()
        observeSources()
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
    }

    private fun initFab() {
        binding.fabAdd.setOnClickListener {
            showEditDialog(null)
        }
    }

    private fun observeSources() {
        lifecycleScope.launch {
            appDb.opdsSourceDao.flowAll().catch {
                AppLog.put("OPDS 源列表更新出错", it)
            }.flowOn(IO).conflate().collect { list ->
                binding.tvEmptyMsg.isGone = list.isNotEmpty()
                adapter.setItems(list)
            }
        }
    }

    override fun edit(source: OpdsSource) {
        showEditDialog(source)
    }

    override fun delete(source: OpdsSource) {
        alert(title = "删除确认") {
            setMessage("确定删除「${source.sourceName}」？")
            yesButton {
                lifecycleScope.launch(IO) {
                    appDb.opdsSourceDao.delete(source)
                }
            }
            noButton()
        }
    }

    private fun showEditDialog(source: OpdsSource?) {
        val dialogTitle = if (source != null) "编辑 OPDS 源" else "添加 OPDS 源"

        alert(title = dialogTitle) {
            val editBinding = DialogOpdsSourceEditBinding.inflate(layoutInflater)

            source?.let { s ->
                editBinding.etName.setText(s.sourceName)
                editBinding.etUrl.setText(s.sourceUrl)
                editBinding.etUsername.setText(s.username ?: "")
                editBinding.etPassword.setText(s.password ?: "")
            }

            customView { editBinding.root }

            okButton {
                val name = editBinding.etName.text?.toString()?.trim() ?: ""
                val url = editBinding.etUrl.text?.toString()?.trim() ?: ""
                val username = editBinding.etUsername.text?.toString()?.trim()
                    .takeIf { !it.isNullOrBlank() }
                val password = editBinding.etPassword.text?.toString()?.trim()
                    .takeIf { !it.isNullOrBlank() }

                if (name.isBlank()) {
                    activity?.toastOnUi("名称不能为空")
                    return@okButton
                }
                if (url.isBlank()) {
                    activity?.toastOnUi("URL 不能为空")
                    return@okButton
                }
                if (!OpdsParser.isValidOpdsUrl(url)) {
                    activity?.toastOnUi("URL 格式不合法，需以 http:// 或 https:// 开头")
                    return@okButton
                }

                lifecycleScope.launch(IO) {
                    if (source != null) {
                        val updated = source.copy(
                            sourceName = name,
                            username = username,
                            password = password
                        )
                        // If URL changed, delete old and insert new
                        if (url != source.sourceUrl) {
                            appDb.opdsSourceDao.delete(source)
                            appDb.opdsSourceDao.insert(updated.copy(sourceUrl = url))
                        } else {
                            appDb.opdsSourceDao.update(updated)
                        }
                    } else {
                        val newSource = OpdsSource(
                            sourceUrl = url,
                            sourceName = name,
                            username = username,
                            password = password,
                            lastAccessTime = System.currentTimeMillis()
                        )
                        appDb.opdsSourceDao.insert(newSource)
                    }
                }
            }
            cancelButton()
        }
    }
}
