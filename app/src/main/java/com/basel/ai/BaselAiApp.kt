package com.basel.ai

import android.app.Application
import com.basel.ai.core.ErrorLog

/**
 * Application entry point, present so diagnostics are armed before anything
 * else runs.
 *
 * Without a custom Application, a failure during model discovery or a crash on
 * a background thread happens before any screen exists and leaves no trace —
 * the app just dies or silently does nothing. Initialising the log here means
 * the next launch can show what went wrong.
 */
class BaselAiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ErrorLog.init(this)
        installCrashHandler()
    }

    /**
     * Records uncaught exceptions, then hands off to the platform handler so
     * the process still dies as Android expects — this reports, it does not
     * swallow. A crash that is caught and hidden is worse than one that isn't.
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { ErrorLog.reportCrash(throwable, thread.name) }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
