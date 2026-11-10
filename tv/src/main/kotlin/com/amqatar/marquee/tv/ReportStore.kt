package com.amqatar.marquee.tv

import android.content.Context
import com.amqatar.marquee.shared.DeviceReport
import com.amqatar.marquee.shared.MarqueeJson
import com.amqatar.marquee.shared.TestReport
import com.amqatar.marquee.shared.TestRun
import java.io.File

/**
 * Results live in the app's external files folder so they can be pulled with
 * `adb pull /sdcard/Android/data/com.amqatar.marquee.tv/files/report.json`.
 */
class ReportStore(context: Context) {
    val file: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "report.json")

    fun runs(): List<TestRun> = runCatching {
        MarqueeJson.decodeFromString(TestReport.serializer(), file.readText()).runs
    }.getOrDefault(emptyList())

    fun append(device: DeviceReport, run: TestRun) = write(device, runs() + run)

    fun clear(device: DeviceReport) = write(device, emptyList())

    private fun write(device: DeviceReport, runs: List<TestRun>) {
        file.parentFile?.mkdirs()
        file.writeText(MarqueeJson.encodeToString(TestReport.serializer(), TestReport(device, runs)))
    }
}
