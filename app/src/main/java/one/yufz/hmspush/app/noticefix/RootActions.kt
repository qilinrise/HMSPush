package one.yufz.hmspush.app.noticefix

import java.util.concurrent.TimeUnit

/**
 * Root-powered actions ported from NoticeFix, which restarts SystemUI by
 * killing its process so the framework restarts it (and the module's
 * SystemUI hooks get reloaded with fresh config and code).
 */
object RootActions {

    /** Returns true when the restart command ran successfully. */
    fun restartSystemUI(): Boolean {
        return runShell("kill -9 $(pidof com.android.systemui) || killall -9 com.android.systemui")
    }

    private fun runShell(command: String): Boolean {
        return runCatching {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            process.outputStream.close()
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            finished && process.exitValue() == 0
        }.getOrDefault(false)
    }
}
