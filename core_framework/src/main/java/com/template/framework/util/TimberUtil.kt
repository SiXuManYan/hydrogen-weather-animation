package com.template.framework.util

import timber.log.Timber

/**
 * Installs the framework logging policy for Timber.
 *
 * Debug builds use [Timber.DebugTree]; release builds install a no-op tree. Initialization is
 * normally performed by [com.template.framework.Framework.init].
 * - 中文：Debug 输出 Logcat，Release 丢弃日志。
 */
object TimberUtil {

    /** Tree that intentionally discards release log messages. */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Release 模式不输出
        }
    }

    /** Installs a debug or no-op tree according to [debug]. */
    fun init(debug: Boolean) {
        if (debug) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
    }
}
