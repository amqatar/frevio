package com.frevio.player.tv

import android.content.Context
import com.frevio.player.shared.DeviceReport
import com.frevio.player.shared.FrevioJson
import com.frevio.player.shared.TestReport
import com.frevio.player.shared.TestRun
import java.io.File

/**
 * Results live in the app's external files folder so they can be pulled with
 * `adb pull /sdcard/Android/data/com.frevio.player/files/report.json`.
 */
class ReportStore(context: Context) {
    val file: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "report.json")

    fun runs(): List<TestRun> = runCatching {
        FrevioJson.decodeFromString(TestReport.serializer(), file.readText()).runs
    }.getOrDefault(emptyList())

    fun append(device: DeviceReport, run: TestRun) = write(device, runs() + run)

    fun clear(device: DeviceReport) = write(device, emptyList())

    private fun write(device: DeviceReport, runs: List<TestRun>) {
        file.parentFile?.mkdirs()
        file.writeText(FrevioJson.encodeToString(TestReport.serializer(), TestReport(device, runs)))
    }
}
