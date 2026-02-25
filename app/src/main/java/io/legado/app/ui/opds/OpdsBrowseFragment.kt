package io.legado.app.ui.opds

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.api.controller.OpdsController
import io.legado.app.data.appDb
import io.legado.app.data.entities.OpdsFeed
import io.legado.app.data.entities.OpdsEntry
import io.legado.app.data.entities.OpdsSource
import io.legado.app.databinding.FragmentOpdsBrowseBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.model.opds.OpdsParseException
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OPDS 目录浏览 Fragment
 * 支持源选择、导航栈浏览、分页加载
 */
class OpdsBrowseFragment : BaseFragment(R.layout.fragment_opds_browse),
    OpdsEntryAdapter.Callback {

    private val binding by viewBinding(FragmentOpdsBrowseBinding::bind)
    private val adapter by lazy { OpdsEntryAdapter(requireContext(), this) }

    /** All available OPDS sources */
    private var sources: List<OpdsSource> = emptyList()

    /** Currently selected source */
    private var currentSource: OpdsSource? = null

    /** Navigation stack: each element is a pair of (url, loaded feed) */
    private val navStack = ArrayDeque<NavItem>()

    /** Current feed being displayed */
    private var currentFeed: OpdsFeed? = null

    /** Whether a page load is in progress */
    private var isLoading = false

    /** Whether more pages are available */
    private var nextPageUrl: String? = null

    /** Active loading job */
    private var loadJob: Job? = null

    /** Whether search results are currently displayed */
    private var isSearchActive = false

    /** Feed that was displayed before search (to restore on clear) */
    private var preSearchFeed: OpdsFeed? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initRecyclerView()
        initSearchBar()
        initRetryButton()
        observeSources()
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter

        // Scroll-to-bottom pagination
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val totalItemCount = layoutManager.itemCount
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (!isLoading && nextPageUrl != null && lastVisible >= totalItemCount - 3) {
                    loadNextPage()
                }
            }
        })
    }

    private fun initRetryButton() {
        binding.btnRetry.setOnClickListener {
            currentSource?.let { loadRootFeed(it) }
        }
    }

    private fun initSearchBar() {
        // Submit search on keyboard "search" action
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch()
                true
            } else {
                false
            }
        }

        // Clear search button
        binding.btnClearSearch.setOnClickListener {
            clearSearch()
        }
    }

    /**
     * Show or hide the search bar based on whether the current feed has a searchUrl.
     */
    private fun updateSearchBarVisibility() {
        val hasSearch = currentFeed?.searchUrl != null
        binding.llSearchBar.isVisible = hasSearch
        if (!hasSearch && isSearchActive) {
            // Feed no longer supports search; reset search state
            isSearchActive = false
            preSearchFeed = null
            binding.etSearch.text?.clear()
            binding.btnClearSearch.isGone = true
        }
    }

    private fun submitSearch() {
        val query = binding.etSearch.text?.toString()?.trim() ?: return
        if (query.isEmpty()) return
        val searchUrl = currentFeed?.searchUrl ?: preSearchFeed?.searchUrl ?: return

        // Save current feed before first search so we can restore it
        if (!isSearchActive && currentFeed != null) {
            preSearchFeed = currentFeed
        }
        isSearchActive = true
        binding.btnClearSearch.isVisible = true

        loadJob?.cancel()
        showLoading()
        loadJob = lifecycleScope.launch {
            try {
                val feed = withContext(IO) {
                    OpdsController.search(
                        searchUrl,
                        query,
                        currentSource?.username,
                        currentSource?.password
                    )
                }
                currentFeed = feed
                nextPageUrl = feed.nextPageUrl
                adapter.setItems(feed.entries)
                if (feed.entries.isEmpty()) {
                    showEmpty("未找到相关结果")
                } else {
                    showContent()
                }
            } catch (e: Exception) {
                AppLog.put("OPDS 搜索失败: ${e.message}", e)
                handleError(e)
            }
        }
    }

    /**
     * Clear search results and restore the previous feed.
     */
    private fun clearSearch() {
        binding.etSearch.text?.clear()
        binding.btnClearSearch.isGone = true
        if (isSearchActive) {
            isSearchActive = false
            val feed = preSearchFeed
            preSearchFeed = null
            if (feed != null) {
                currentFeed = feed
                nextPageUrl = feed.nextPageUrl
                adapter.setItems(feed.entries)
                if (feed.entries.isEmpty()) {
                    showEmpty("该目录暂无内容")
                } else {
                    showContent()
                }
                updateSearchBarVisibility()
            }
        }
    }

    private fun observeSources() {
        AppLog.put("OPDS observeSources() called")
        lifecycleScope.launch {
            AppLog.put("OPDS flowAll() collecting...")
            appDb.opdsSourceDao.flowAll().catch {
                AppLog.put("OPDS 源列表加载出错", it)
            }.flowOn(IO).conflate().collect { list ->
                AppLog.put("OPDS sources collected: ${list.size} items")
                sources = list
                updateSpinner(list)
            }
        }
    }

    private fun updateSpinner(list: List<OpdsSource>) {
        AppLog.put("OPDS updateSpinner: ${list.size} sources")
        val names = list.map { it.sourceName }
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            names
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSource.adapter = spinnerAdapter
        binding.spinnerSource.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val source = list.getOrNull(position) ?: return
                    AppLog.put("OPDS spinner selected: ${source.sourceName} (${source.sourceUrl}), currentSource=${currentSource?.sourceName}")
                    if (source != currentSource) {
                        currentSource = source
                        navStack.clear()
                        resetSearchState()
                        loadRootFeed(source)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        if (list.isEmpty()) {
            showEmpty("暂无 OPDS 源，请先在「源管理」中添加")
        }
    }

    private fun loadRootFeed(source: OpdsSource) {
        AppLog.put("OPDS loadRootFeed: ${source.sourceUrl}")
        loadFeed(source.sourceUrl, source.username, source.password, pushToStack = false)
    }

    private fun loadFeed(
        url: String,
        username: String? = currentSource?.username,
        password: String? = currentSource?.password,
        pushToStack: Boolean = true
    ) {
        AppLog.put("OPDS loadFeed: $url")
        loadJob?.cancel()
        showLoading()
        loadJob = lifecycleScope.launch {
            try {
                val feed = withContext(IO) {
                    OpdsController.fetchFeed(url, username, password)
                }
                AppLog.put("OPDS feed loaded: ${feed.title}, entries=${feed.entries.size}")
                if (pushToStack && currentFeed != null) {
                    navStack.addLast(NavItem(url = currentFeedUrl(), feed = currentFeed!!))
                }
                currentFeed = feed
                nextPageUrl = feed.nextPageUrl
                adapter.setItems(feed.entries)
                if (feed.entries.isEmpty()) {
                    showEmpty("该目录暂无内容")
                } else {
                    showContent()
                }
                updateSearchBarVisibility()
            } catch (e: Exception) {
                AppLog.put("OPDS Feed 加载失败: ${e.message}", e)
                handleError(e)
            }
        }
    }

    private fun loadNextPage() {
        val url = nextPageUrl ?: return
        if (isLoading) return
        isLoading = true
        loadJob = lifecycleScope.launch {
            try {
                val feed = withContext(IO) {
                    OpdsController.fetchFeed(
                        url,
                        currentSource?.username,
                        currentSource?.password
                    )
                }
                nextPageUrl = feed.nextPageUrl
                adapter.addItems(feed.entries)
            } catch (e: Exception) {
                AppLog.put("OPDS 加载下一页失败: ${e.message}", e)
                val msg = when (e) {
                    is IOException -> "网络连接失败，加载更多失败"
                    is OpdsParseException -> "OPDS 格式错误：无法解析返回的内容"
                    else -> "加载更多失败: ${e.message}"
                }
                activity?.toastOnUi(msg)
            } finally {
                isLoading = false
            }
        }
    }

    override fun onEntryClick(entry: OpdsEntry) {
        if (entry.isNavigation) {
            val navUrl = entry.navigationUrl ?: return
            resetSearchState()
            loadFeed(navUrl)
        } else {
            OpdsBookDetailDialog.show(
                context = requireContext(),
                lifecycleOwner = viewLifecycleOwner,
                entry = entry,
                source = currentSource
            )
        }
    }

    /**
     * Handle back press: clear search first, then pop navigation stack, or return false
     */
    fun onBackPressed(): Boolean {
        if (isSearchActive) {
            clearSearch()
            return true
        }
        if (navStack.isNotEmpty()) {
            val prev = navStack.removeLast()
            currentFeed = prev.feed
            nextPageUrl = prev.feed.nextPageUrl
            adapter.setItems(prev.feed.entries)
            if (prev.feed.entries.isEmpty()) {
                showEmpty("该目录暂无内容")
            } else {
                showContent()
            }
            updateSearchBarVisibility()
            return true
        }
        return false
    }

    private fun currentFeedUrl(): String {
        return currentFeed?.links?.firstOrNull { it.rel == "self" }?.href
            ?: currentSource?.sourceUrl ?: ""
    }

    /**
     * Reset search state without restoring previous feed.
     * Used when switching sources or navigating away.
     */
    private fun resetSearchState() {
        isSearchActive = false
        preSearchFeed = null
        binding.etSearch.text?.clear()
        binding.btnClearSearch.isGone = true
    }

    // region Error classification

    /**
     * Classify the exception and show the appropriate error UI.
     * - IOException → network error with retry button
     * - OpdsParseException → format error, no retry (retrying won't help)
     * - Other → generic message with retry button
     */
    private fun handleError(e: Exception) {
        when (e) {
            is IOException -> showError("网络连接失败，请检查网络后重试", showRetry = true)
            is OpdsParseException -> showError("OPDS 格式错误：无法解析返回的内容", showRetry = false)
            else -> showError(e.message ?: "加载失败", showRetry = true)
        }
    }

    // endregion

    // region UI state helpers

    private fun showLoading() {
        isLoading = true
        binding.progressBar.isVisible = true
        binding.recyclerView.isGone = true
        binding.tvEmpty.isGone = true
        binding.llError.isGone = true
    }

    private fun showContent() {
        isLoading = false
        binding.progressBar.isGone = true
        binding.recyclerView.isVisible = true
        binding.tvEmpty.isGone = true
        binding.llError.isGone = true
    }

    private fun showEmpty(message: String) {
        isLoading = false
        binding.progressBar.isGone = true
        binding.recyclerView.isGone = true
        binding.tvEmpty.isVisible = true
        binding.tvEmpty.text = message
        binding.llError.isGone = true
    }

    private fun showError(message: String, showRetry: Boolean = true) {
        isLoading = false
        binding.progressBar.isGone = true
        binding.recyclerView.isGone = true
        binding.tvEmpty.isGone = true
        binding.llError.isVisible = true
        binding.tvError.text = message
        binding.btnRetry.isVisible = showRetry
    }

    // endregion

    private data class NavItem(
        val url: String,
        val feed: OpdsFeed
    )
}
