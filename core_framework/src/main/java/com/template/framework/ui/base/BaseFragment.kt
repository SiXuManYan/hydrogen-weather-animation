package com.template.framework.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

/**
 * ViewBinding-based Fragment foundation with ordered setup hooks.
 *
 * [initView], [initListener], [observeViewModel], and [initData] run in that order from
 * `onViewCreated`. The binding is cleared in `onDestroyView`, allowing the Fragment instance to
 * outlive its view safely.
 *
 * - 中文：统一管理 Fragment 的 ViewBinding 与视图初始化顺序。
 *
 * ## Usage
 * ```kotlin
 * class DemoFragment : BaseFragment<FragmentDemoBinding>() {
 *     override fun initViewBinding(inflater, container) = FragmentDemoBinding.inflate(inflater, container, false)
 *     override fun initView() { /* 初始化视图 */ }
 *     override fun observeViewModel() { /* 观察 ViewModel */ }
 * }
 * ```
 *
 */
abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null

    /** Binding valid only between `onCreateView` and `onDestroyView`. */
    protected val binding: VB
        get() = _binding ?: throw IllegalStateException(
            "Binding should not be accessed before onCreateView or after onDestroyView"
        )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = initViewBinding(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        initListener()
        observeViewModel()
        initData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** Inflates or creates the binding for this Fragment view. */
    protected abstract fun initViewBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    /** Configures initial view state. */
    protected open fun initView() {}

    /** Registers UI listeners after [initView]. */
    protected open fun initListener() {}

    /** Starts lifecycle-aware observation after listeners are registered. */
    protected open fun observeViewModel() {}

    /** Starts initial data loading after [observeViewModel]. */
    protected open fun initData() {}

    /** Hides the keyboard using the Fragment root view token, if the view exists. */
    protected fun hideKeyboard() {
        val view = view ?: return
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /** Hides the keyboard using [view]'s window token. */
    protected fun hideKeyboard(view: View) {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
