package com.premiumcar.launcher.system

import android.os.Process
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Uma única thread de baixa prioridade para consultas ao sistema (PackageManager etc.),
 * fora da thread principal. Sem trabalho periódico: só executa o que a tela pede.
 */
object Background {

    @Volatile
    var executor: Executor = Executors.newSingleThreadExecutor { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "pc-background").apply { isDaemon = true }
    }
}
