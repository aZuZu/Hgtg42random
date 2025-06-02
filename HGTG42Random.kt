package com.azuzu.ahl_srng

import kotlin.math.absoluteValue
import kotlin.random.Random

class HGTG42Random(
    var state: Long,
    val inc: Long = System.nanoTime() or 1L,
) : Random() {

    private var pair: Pair<Int, Int>
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
        val random = Random(entropySource xor i)
        val shiftPair = Helpers.staticShiftPairsCandidates().random(random)
        pair = shiftPair
    }

    companion object {
        private val instances: MutableMap<IntRange, HGTG42Random> = mutableMapOf()
        private val sharedPreferences = MyApplication
        private const val MULTIPLIER = 6364136223846793005L
        private val hgtgMap by lazy { sharedPreferences.loadCRNGParams() }

        fun preloadInstances() {
            if (hgtgMap.isNotEmpty()) {
                logDebug("HGTG42Random", "Preloading HGTG42 Instances...")
                hgtgMap.forEach { (range, params) ->
                    val (seed, i, score) = params
                    instances[range] = HGTG42Random(seed, i)
                    logDebug(
                        "HGTG42Random",
                        "? Preloaded range $range with (seed=$seed, i=$i, Score=$score)"
                    )
                }
            }
        }

        fun getInstance(numberRange: IntRange): HGTG42Random {
            instances[numberRange]?.let { return it }
            val hgtg = hgtgMap[numberRange] ?: TuneResult(
                System.nanoTime(),
                1442695040888963407L,
                Double.MAX_VALUE
            )
            val (seed, i) = hgtg
            val newInstance = HGTG42Random(seed, i)
            instances[numberRange] = newInstance
            logDebug("HGTG42Random", "?? Created NEW instance for $numberRange")
            return newInstance
        }
    }

    private fun safeShift(value: Long, shift: Int): Long = value ushr shift.coerceIn(0, 63)

    private fun generateTuningShifts(pair: Pair<Int, Int>): List<Int> {
        val (xorBase, rotBase) = pair
        val xorShifts = listOf(
            xorBase,
            xorBase + 10,
            xorBase + 11,
            xorBase + 21,
            xorBase + 32,
            xorBase + 42
        ).map { it.coerceIn(0, 63) }
        val rotShifts = listOf(
            rotBase - 42,
            rotBase - 52,
            rotBase - 31,
            rotBase - 41,
            rotBase,
            rotBase - 10
        ).map { it.coerceIn(0, 63) }
        return xorShifts + rotShifts
    }

    override fun nextBits(bitCount: Int): Int {
        require(bitCount in 1..32)
        state = state * MULTIPLIER + i

        val shifts = generateTuningShifts(pair)

        val xorState = state xor midFilter
        val xorshiftedLow =
            (safeShift(xorState, shifts[0]) xor safeShift(entropySource, shifts[1])) and lowerFilter
        val xorshiftedMid =
            (safeShift(xorState, shifts[2]) xor safeShift(entropySource, shifts[3])) and midFilter
        val xorshiftedHigh = (safeShift(xorState, shifts[4]) xor safeShift(
            entropySource,
            shifts[5]
        )) and higherFilter

        val rotLow = (safeShift(state, shifts[6]) xor safeShift(entropySource, shifts[7])).toInt()
        val rotMid = (safeShift(state, shifts[8]) xor safeShift(entropySource, shifts[9])).toInt()
        val rotHigh =
            (safeShift(state, shifts[10]) xor safeShift(entropySource, shifts[11])).toInt()

        val mixLow = (xorshiftedLow ushr rotLow) or (xorshiftedLow shl ((-rotLow) and 31))
        val mixMid = (xorshiftedMid ushr rotMid) or (xorshiftedMid shl ((-rotMid) and 31))
        val mixHigh = (xorshiftedHigh ushr rotHigh) or (xorshiftedHigh shl ((-rotHigh) and 31))

        val result = mixLow xor mixMid xor mixHigh
        return if (bitCount == 32) result.toInt() else (result and ((1L shl bitCount) - 1)).toInt()
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

        fun staticShiftPairsCandidates(): List<Pair<Int, Int>> {
            return listOf(
                Pair(28, 34), Pair(8, 54), Pair(13, 49), Pair(9, 53), Pair(50, 12),
                Pair(18, 44), Pair(49, 13), Pair(25, 37), Pair(21, 41), Pair(32, 30)
            )
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
