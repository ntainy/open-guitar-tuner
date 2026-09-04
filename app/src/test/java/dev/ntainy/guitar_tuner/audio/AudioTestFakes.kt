package dev.ntainy.guitar_tuner.audio

import dev.ntainy.guitar_tuner.dsp.FrameAssembler
import dev.ntainy.guitar_tuner.dsp.NoteMath
import dev.ntainy.guitar_tuner.dsp.PitchDetector
import dev.ntainy.guitar_tuner.dsp.PitchEstimate
import dev.ntainy.guitar_tuner.dsp.PitchSmoother
import dev.ntainy.guitar_tuner.dsp.TargetResolver
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

const val TEST_CHUNK = 1024

val USB_DEVICE = AudioInputDevice(10, "usb:NUX MP-3", "NUX MP-3 (USB)", InputKind.USB)
val MIC_DEVICE = AudioInputDevice(2, AudioInputDevice.BUILTIN_KEY, "Built-in microphone", InputKind.BUILTIN_MIC)
val WIRED_DEVICE = AudioInputDevice(5, "wired", "Wired headset", InputKind.WIRED_HEADSET)

/** A constant chunk whose RMS equals [amplitude]. */
fun chunkOf(amplitude: Float = 0.1f): FloatArray = FloatArray(TEST_CHUNK) { amplitude }

fun rmsOf(frame: FloatArray): Double {
    var sum = 0.0
    for (x in frame) sum += x.toDouble() * x
    return if (frame.isEmpty()) 0.0 else sqrt(sum / frame.size)
}

class FakeAudioInputMonitor(initial: List<AudioInputDevice>) : AudioInputMonitor {
    val list = MutableStateFlow(initial)
    override val devices: Flow<List<AudioInputDevice>> = list
}

/**
 * Emits [scripted] chunks, then throws [failure] if set, then whatever is pushed with [send] until cancelled.
 * Records whether it was collected and whether the collector cancelled it.
 */
class FakeAudioSource(
    override val device: AudioInputDevice,
    scripted: List<FloatArray> = emptyList(),
    private val failure: Throwable? = null,
    override val sampleRate: Int = 48_000,
) : AudioSource {
    private val scripted = scripted.toList()
    private val live = Channel<FloatArray>(Channel.UNLIMITED)

    var collected = false
        private set
    var cancelled = false
        private set

    fun send(chunk: FloatArray, times: Int = 1) {
        repeat(times) { check(live.trySend(chunk).isSuccess) }
    }

    override fun samples(): Flow<FloatArray> = flow {
        collected = true
        try {
            scripted.forEach { emit(it) }
            failure?.let { throw it }
            for (chunk in live) emit(chunk)
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        }
    }
}

/** Source factory that remembers every device it was asked for and every source it produced. */
class RecordingSourceFactory(
    private val create: (AudioInputDevice) -> FakeAudioSource = { FakeAudioSource(it) },
) : (AudioInputDevice) -> AudioSource {
    val devices = mutableListOf<AudioInputDevice>()
    val sources = mutableListOf<FakeAudioSource>()

    val latest: FakeAudioSource get() = sources.last()

    override fun invoke(device: AudioInputDevice): AudioSource {
        devices += device
        return create(device).also { sources += it }
    }
}

/** Every pushed chunk is one frame. */
class PassThroughAssembler(override val frameSize: Int = TEST_CHUNK) : FrameAssembler {
    override val hopSize: Int = frameSize
    var resets = 0
        private set

    override fun push(samples: FloatArray, count: Int, onFrame: (FloatArray) -> Unit) = onFrame(samples.copyOf(count))

    override fun reset() {
        resets++
    }
}

/** Returns queued frequencies in order (null = silence), then [fallback] forever. */
class ScriptedPitchDetector(
    var fallback: Double? = null,
    override val sampleRate: Int = 48_000,
    override val frameSize: Int = TEST_CHUNK,
) : PitchDetector {
    val queue = ArrayDeque<Double?>()
    var calls = 0
        private set

    override fun detect(frame: FloatArray): PitchEstimate? {
        calls++
        val hz = if (queue.isEmpty()) fallback else queue.removeFirst()
        return hz?.let { PitchEstimate(it, CONFIDENCE, rmsOf(frame)) }
    }

    companion object {
        const val CONFIDENCE = 0.9
    }
}

class IdentitySmoother : PitchSmoother {
    var resets = 0
        private set

    override fun push(estimate: PitchEstimate?): Double? = estimate?.frequencyHz

    override fun reset() {
        resets++
    }
}

class NearestStringResolver : TargetResolver {
    var resets = 0
        private set

    override fun resolve(pitchHz: Double, targetsHz: DoubleArray): Int =
        targetsHz.indices.minByOrNull { abs(NoteMath.cents(pitchHz, targetsHz[it])) } ?: 0

    override fun reset() {
        resets++
    }
}
