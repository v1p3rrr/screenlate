package com.vpr.screenlate.core.common.log

/**
 * Stands in for [android.util.Log]: every line goes to logcat and to the app's own log file ([AppLog]), which keeps
 * lines far longer than logcat and also on phones that show an app none of its logcat lines. The rules for logcat apply
 * unchanged: no recognized text, words, note contents or URLs with terms.
 */
object Log {
    fun d(tag: String, msg: String, tr: Throwable? = null): Int {
        AppLog.write('D', tag, msg, tr)
        return android.util.Log.d(tag, msg, tr)
    }

    fun i(tag: String, msg: String, tr: Throwable? = null): Int {
        AppLog.write('I', tag, msg, tr)
        return android.util.Log.i(tag, msg, tr)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null): Int {
        AppLog.write('W', tag, msg, tr)
        return android.util.Log.w(tag, msg, tr)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null): Int {
        AppLog.write('E', tag, msg, tr)
        return android.util.Log.e(tag, msg, tr)
    }
}
