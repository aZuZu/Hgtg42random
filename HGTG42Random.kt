package com.azuzu.ahl_srng

import kotlin.math.absoluteValue
import kotlin.random.Random

class HGTG42Random(
    var state: Long,
    val inc: Long = System.nanoTime() or 1L,
    val pair: Pair<Int, Int>
) : Random() {

    private val i: Long
    private var entropySource = state

    private val golden = 0x9E3779B97F4A7C15uL.toLong()
    private val lowerFilter = 0xCAFEBABECAFEBABEuL.toLong()
    private val midFilter = 0xFEEDFACEFEEDFACEuL.toLong()
    private val higherFilter = 0xDEADBEEFDEADBEEFuL.toLong()

    init {
        val xorValSeed = (state xor 0xA5A5A5A5A5A5A5A5uL.toLong())
        val xorValInc = (inc xor 0xA5A5A5A5A5A5A5A5uL.toLong())
        val mixed = xorValSeed * xorValInc
        val low = (mixed xor lowerFilter xor golden) xor (entropySource ushr 42)
        val high = (mixed xor higherFilter xor golden) xor (entropySource ushr 42)
        i = (low and high) or 1L
        entropySource = mixed
    }

    companion object {
        private val instances: MutableMap<IntRange, HGTG42Random> = mutableMapOf()
        private val sharedPreferences = MyApplication
        private const val MULTIPLIER = 6364136223846793005L
        private val hgtgMap by lazy { sharedPreferences.getHGTGSettings() }

        fun preloadInstances() {
            if (hgtgMap.isNotEmpty()) {
                logDebug("HGTG42Random", "Preloading HGTG42 Instances...")
                hgtgMap.forEach { (range, params) ->
                    val (seed, i, pair, score) = params
                    instances[range] = HGTG42Random(seed, i, pair)
                    logDebug(
                        "HGTG42Random",
                        "? Preloaded range $range with (seed=$seed, i=$i, pair=$pair Score=$score)"
                    )
                }
            }
        }

        fun getInstance(numberRange: IntRange): HGTG42Random {
            instances[numberRange]?.let { return it }
            val hgtg = hgtgMap[numberRange] ?: TuneResult(
                System.nanoTime(),
                1442695040888963407L,
                Pair(8,54),
                Double.MAX_VALUE
            )
            val (seed, i, pair, score) = hgtg
            val newInstance = HGTG42Random(seed, i, pair)
            instances[numberRange] = newInstance
            logDebug("HGTG42Random", "?? Created NEW instance for range $numberRange with (seed=$seed, i=$i, pair=$pair Score=$score)")
            return newInstance
        }
    }

    private fun safeShift(value: Long, shift: Int): Long = value ushr shift.coerceIn(0, 63)

    private fun generateTuningShifts(pair: Pair<Int, Int>): List<Int> {
        val (xorBase, rotBase) = pair

        val xorOffsets = listOf(-42, -32, -21, -10, 0, 10, 21, 32, 42)
        val rotOffsets = listOf(-42, -32, -21, -10, 0, 10, 21, 32, 42)

        val xorShifts = xorOffsets.map { (xorBase + it).coerceIn(4, 58) }
        val rotShifts = rotOffsets.map { (rotBase + it).coerceIn(4, 58) }

        return xorShifts + rotShifts
    }

    override fun nextBits(bitCount: Int): Int {
        require(bitCount in 1..32)
        state = state * MULTIPLIER + i

        val shifts = generateTuningShifts(pair)

        val xorState = state xor midFilter

        val xorshifted = shifts.take(9).mapIndexed { index, shift ->
            (safeShift(xorState, shift) xor safeShift(entropySource, shift)).andWhen(index)
        }

        val rotshifted = shifts.drop(9).mapIndexed { index, shift ->
            (safeShift(state, shift) xor safeShift(entropySource, shift)).toInt()
        }

        // Basic way to combine — you can fine-tune this mixer style
        val mix = xorshifted.zip(rotshifted).map { (x, r) ->
            (x ushr r) or (x shl ((-r) and 31))
        }

        val result = mix.reduce { acc, v -> acc xor v }
        return if (bitCount == 32) result.toInt() else (result and ((1L shl bitCount) - 1)).toInt()
    }

    // Little helper to select your filter windows
    private fun Long.andWhen(index: Int): Long {
        return when (index % 3) {
            0 -> this and lowerFilter
            1 -> this and midFilter
            else -> this and higherFilter
        }
    }

    override fun nextInt(): Int = nextBits(32)
    override fun nextInt(until: Int): Int = nextInt(0, until)
    override fun nextInt(from: Int, until: Int): Int {
        require(from < until)
        return from + (nextBits(31).absoluteValue % (until - from)).toInt()
    }

    override fun nextLong(): Long = (nextBits(32).toLong() shl 32) or nextBits(32).toLong()
    override fun nextBoolean(): Boolean = nextBits(1) != 0
    override fun nextDouble(): Double = nextBits(53).toLong() / (1L shl 53).toDouble()
    override fun nextDouble(from: Double, until: Double): Double {
        require(from < until)
        return from + (until - from) * nextDouble()
    }

    object Helpers {
        fun makeStaticTuningCandidates(): Pair<List<Long>, List<Long>> {
            val random = java.util.Random()
            val seeds = List(10) { random.nextLong().absoluteValue % 500_000L + 50_000L }
            val incs = List(10) { -1L * (random.nextLong().absoluteValue % 500_000L + 10_000L) }
            return seeds to incs
        }

        fun makeTuningCandidatesFromCalendar(): Pair<List<Long>, List<Long>> {
            val oddMonths = listOf(1, 3, 5, 7, 9, 11)
            val evenMonths = listOf(2, 4, 6, 8, 10, 12)

            val nanoOffsets = List(oddMonths.size) { System.nanoTime() % 10_000_000L }

            val inc = oddMonths.mapIndexed { idx, month ->
                val base = (month * 1_000_000_000L) + nanoOffsets[idx]
                if (base % 2 == 0L) base + 1 else base
            }

            val seed = evenMonths.mapIndexed { idx, month ->
                val base = (month * 1_000_000_000L) + nanoOffsets[idx]
                if (base % 2 == 1L) base + 1 else base
            }

            return Pair(seed, inc)
        }
    }
}

fun <T> List<T>.aZuZuShuffle(random: Random): List<T> {
    val list = toMutableList()
    var swapCount = 0
    while (swapCount < list.size) {
        val first = random.nextInt(list.size)
        val second = random.nextInt(list.size)
        if (first != second) {
            val temp = list[first]
            list[first] = list[second]
            list[second] = temp
            swapCount++
        }
    }
    return list
}
