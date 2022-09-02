package org.jetbrains.bsp.bazel.bazelrunner.outputs

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.Future

abstract class OutputProcessor(private val process: Process, vararg loggers: OutputHandler) {
  val stdoutCollector = OutputCollector()
  val stderrCollector = OutputCollector()

  private val executorService = Executors.newCachedThreadPool()
  protected val runningProcessors = mutableListOf<Future<*>>()

  init {
    start(process.inputStream, stdoutCollector, *loggers)
    start(process.errorStream, stderrCollector, *loggers)
  }

  protected open fun shutdown() {
    executorService.shutdown()
  }

  protected abstract fun isRunning(): Boolean

  protected fun start(inputStream: InputStream, vararg handlers: OutputHandler) {
    val runnable = Runnable {
      try {
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
          var prevLine: String? = null

          while (!Thread.currentThread().isInterrupted) {
            val line = reader.readLine() ?: return@Runnable
            if (line == prevLine) continue
            prevLine = line
            if (isRunning()) {
              handlers.forEach { it.onNextLine(line) }
            } else {
              break
            }
          }
        }
      } catch (e: IOException) {
        if (Thread.currentThread().isInterrupted) return@Runnable
        throw RuntimeException(e)
      }
    }

    executorService.submit(runnable).also { runningProcessors.add(it) }
  }

  fun waitForExit(): Int {
    val exitCode = process.waitFor()
    shutdown()
    return exitCode
  }
}