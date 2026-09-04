package dev.ntainy.guitar_tuner.audio

import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.dsp.HysteresisTargetResolver
import dev.ntainy.guitar_tuner.dsp.MedianEmaSmoother
import dev.ntainy.guitar_tuner.dsp.NoteMath
import dev.ntainy.guitar_tuner.dsp.RingBufferFrameAssembler
import dev.ntainy.guitar_tuner.dsp.YinPitchDetector
import dev.ntainy.guitar_tuner.fakes.InMemoryTuningsRepository
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue

/**
 * Replays a recording through the real engine and the real DSP chain, one capture chunk at a time, and writes what
 * the screen would have shown for every analysis frame. This is the JVM stand-in for `adb logcat -s TunerFrames:V`
 * when the guitar and the interface are somewhere else: a recording made on the phone through the same input is
 * enough to reproduce a complaint on the desk.
 *
 * Skipped unless the Gradle property `tuner.replay.file` names a 48 kHz mono recording: raw little-endian float32
 * (any extension but `.wav`) or a WAV file (PCM 16-bit or float32; extra channels are averaged). Convert anything
 * else with `ffmpeg -i in.m4a -ac 1 -ar 48000 -f f32le out.f32`. `app/build.gradle.kts` forwards the
 * `tuner.replay.*` properties into the test JVM as system properties.
 *
 * Optional properties:
 * - `tuner.replay.gainDb` scales the recording before it reaches the engine, to simulate a quieter or hotter
 *   interface than the one that made it.
 * - `tuner.replay.sensitivityDb` is the per-device sensitivity the user would have set for this input.
 * - `tuner.replay.out` is the TSV to write; default `<file>.replay.tsv`.
 *
 * ```
 * ./gradlew :app:testDebugUnitTest --tests '*RecordingReplayTest*' -Ptuner.replay.file=$HOME/sample.f32
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordingReplayTest {

    @Test
    fun replayRecording() = runTest {
        val path = System.getProperty("tuner.replay.file")?.takeIf { it.isNotBlank() }
        assumeTrue("tuner.replay.file is not set; nothing to replay", path != null)
        val file = File(path!!.replaceFirst("~", System.getProperty("user.home")))
        assumeTrue("$file does not exist", file.isFile)

        val preGainDb = System.getProperty("tuner.replay.gainDb")?.toDoubleOrNull() ?: 0.0
        val sensitivityDb = System.getProperty("tuner.replay.sensitivityDb")?.toDoubleOrNull() ?: 0.0
        val out = System.getProperty("tuner.replay.out")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(file.parentFile, file.name + ".replay.tsv")

        val samples = readRecording(file)
        val preGain = 10.0.pow(preGainDb / 20.0).toFloat()
        if (preGain != 1f) for (i in samples.indices) samples[i] *= preGain

        val device = USB_DEVICE
        val settings = MutableStateFlow(
            TunerSettings(sensitivityDb = if (sensitivityDb == 0.0) emptyMap() else mapOf(device.key to sensitivityDb)),
        )
        val factory = RecordingSourceFactory()
        val engine = AudioTunerEngine(
            scope = backgroundScope,
            inputMonitor = FakeAudioInputMonitor(listOf(device)),
            sourceFactory = factory,
            detector = YinPitchDetector(),
            smoother = MedianEmaSmoother(medianWindow = 5, alpha = 0.3),
            resolver = HysteresisTargetResolver(),
            assembler = RingBufferFrameAssembler(),
            tuningFlow = MutableStateFlow(InMemoryTuningsRepository.STANDARD),
            settingsFlow = settings,
            analysisDispatcher = StandardTestDispatcher(testScheduler),
        )
        engine.onPermissionResult(true)
        engine.start()
        testScheduler.runCurrent()

        // A second detector on the same gained chunks shows what YIN saw before the gate, guard and smoother.
        val rawAssembler = RingBufferFrameAssembler()
        val rawDetector = YinPitchDetector()
        val rawGain = 10.0.pow(sensitivityDb / 20.0).toFloat()

        val chunk = AudioRecordSource.DEFAULT_CHUNK_SIZE
        val targets = InMemoryTuningsRepository.STANDARD.frequencies(settings.value.a4Hz)
        var frames = 0
        var shown = 0
        out.printWriter().use { w ->
            w.println("t\traw_hz\traw_conf\traw_rms\tshown_hz\ttarget\tcents\tin_tune\tlevel\tconf")
            var offset = 0
            while (offset + chunk <= samples.size) {
                val piece = samples.copyOfRange(offset, offset + chunk)
                offset += chunk
                var raw: dev.ntainy.guitar_tuner.dsp.PitchEstimate? = null
                var framed = false
                val gained = if (rawGain == 1f) piece else FloatArray(piece.size) { piece[it] * rawGain }
                rawAssembler.push(gained) { frame ->
                    framed = true
                    raw = rawDetector.detect(frame)
                }
                factory.latest.send(piece)
                testScheduler.runCurrent()
                if (!framed) continue
                frames++
                val s = engine.state.value
                if (s.pitchHz != null) shown++
                val t = offset.toDouble() / DEFAULT_SAMPLE_RATE
                w.println(
                    String.format(
                        Locale.US,
                        "%.3f\t%.2f\t%.3f\t%.5f\t%.2f\t%s\t%s\t%b\t%.5f\t%.2f",
                        t,
                        raw?.frequencyHz ?: 0.0,
                        raw?.confidence ?: 0.0,
                        raw?.rms ?: rms(gained),
                        s.pitchHz ?: 0.0,
                        s.targetIndex?.let { i -> NoteMath.name(InMemoryTuningsRepository.STANDARD.strings[i]) } ?: "-",
                        s.centsOff?.let { c -> String.format(Locale.US, "%+.1f", c) } ?: "-",
                        s.inTune,
                        s.level,
                        s.confidence,
                    ),
                )
            }
        }
        val targetList = targets.joinToString { String.format(Locale.US, "%.1f", it) }
        println(
            "Replayed ${samples.size / DEFAULT_SAMPLE_RATE} s of $file " +
                "(pre-gain $preGainDb dB, sensitivity $sensitivityDb dB): " +
                "$frames frames, $shown with a reading (targets $targetList); wrote $out",
        )
    }

    private fun rms(frame: FloatArray): Double {
        var sum = 0.0
        for (x in frame) sum += x.toDouble() * x
        return sqrt(sum / frame.size)
    }

    private fun readRecording(file: File): FloatArray {
        val bytes = file.readBytes()
        return if (file.extension.equals("wav", ignoreCase = true)) readWav(bytes) else readRawFloat(bytes)
    }

    private fun readRawFloat(bytes: ByteArray): FloatArray {
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(floats.remaining()).also { floats.get(it) }
    }

    /** Minimal RIFF/WAVE reader: `fmt ` then `data`, PCM16 or IEEE float32, any channel count averaged to mono. */
    private fun readWav(bytes: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "not a WAV file" }
        var pos = 12
        var format = 0
        var channels = 1
        var sampleRate = 0
        var bits = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val size = buf.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    format = buf.getShort(body).toInt()
                    channels = buf.getShort(body + 2).toInt()
                    sampleRate = buf.getInt(body + 4)
                    bits = buf.getShort(body + 14).toInt()
                }

                "data" -> {
                    require(sampleRate == DEFAULT_SAMPLE_RATE) {
                        "WAV is $sampleRate Hz, the chain expects $DEFAULT_SAMPLE_RATE"
                    }
                    val frameBytes = channels * bits / 8
                    val count = size / frameBytes
                    val mono = FloatArray(count)
                    for (i in 0 until count) {
                        var sum = 0.0
                        for (c in 0 until channels) {
                            val at = body + i * frameBytes + c * bits / 8
                            sum += when {
                                format == 3 && bits == 32 -> buf.getFloat(at).toDouble()
                                format == 1 && bits == 16 -> buf.getShort(at) / 32768.0
                                else -> error("unsupported WAV: format $format, $bits bits")
                            }
                        }
                        mono[i] = (sum / channels).toFloat()
                    }
                    return mono
                }
            }
            pos = body + size + (size and 1)
        }
        error("WAV has no data chunk")
    }

    private companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000
    }
}
