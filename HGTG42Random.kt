package com.azuzu.ahl_srng

import kotlin.math.absoluteValue
import kotlin.random.Random

class HGTG42Random(
    var state: Long,
    val inc: Long = System.nanoTime() or 1L,
    val pair: Pair<Int, Int> = Pair(18, 54),
): Random() {
    private val shiftPair: Pair<Int, Int>
    private val i: Long
    private var initialSeed = state
    val golden = 0x9E3779B97F4A7C15uL.toLong()
    val lowerFilter = 0xCAFEBABECAFEBABEuL.toLong()
    val midFilter = 0xFEEDFACEFEEDFACEuL.toLong()
    val higherFilter = 0xDEADBEEFDEADBEEFuL.toLong()

    init {
        val xorValSeed = (state xor 0xA5A5A5A5A5A5A5A5uL.toLong())
        val xorValInc = (inc xor 0xA5A5A5A5A5A5A5A5uL.toLong())
        val mixed = xorValSeed * xorValInc
        val low = (mixed xor lowerFilter xor golden) xor (initialSeed ushr 42)
        val high = (mixed xor higherFilter xor golden) xor (initialSeed ushr 42)
        i = (low and high) or 1L
        shiftPair = resolveShiftPair(pair, seed, i)
    }

    companion object {
        private val instances: MutableMap<IntRange, HGTG42Random> = mutableMapOf()
        private val sharedPreferences = MyApplication
        private const val MULTIPLIER = 6364136223846793005L
        private val hgtgMap by lazy { sharedPreferences.loadCRNGParams() }

        private fun tuningShiftPairs(
           minFirst: Int = 14,
           minSecond: Int = 15,
        ): List<Pair<Int, Int>> {

            val pairs = mutableListOf<Pair<Int, Int>>()
            for (first in minFirst..(minFirst * 3)) {
                 for (second in (minSecond * 3) downTo minSecond) {
                     if (((first + 9) % 2 == 0) && ((second - 37) % 2 == 1)) {
                             pairs.add(Pair(first, second))
                      }
                  }
             }
             return pairs
        }

        fun preloadInstances() {
            if (hgtgMap.isNotEmpty()) {
                logDebug("HGTG42Random", "Preloading HGTG42 Instances...")
                hgtgMap.forEach { (range, params) ->
                    val (seed, i, pair, score) = params
                    instances[range] = HGTG42Random(seed)
                    logDebug("HGTG42Random", "✅ Preloaded range $range with (seed=$seed, i=$i, Score=$score)")
                }
            }
        }

        fun getInstance(numberRange: IntRange): HGTG42Random {
            instances[numberRange]?.let { return it }
            val hgtg = hgtgMap[numberRange] ?: TuneResult(System.nanoTime(), 1442695040888963407L, Pair(18,54), Double.MAX_VALUE)
            val (seed, i, _) = hgtg

            val newInstance = HGTG42Random(seed, i, shiftPair)

            instances[numberRange] = newInstance
            logDebug("HGTG42Random", "⚠️ Created NEW instance with tuned pair=$pair)")
            return newInstance
        }

        internal fun internalHGTG42RandomTune(inc: Long): Pair<Int, Int> {
            var bestPair = Pair(0, 0)
            var maxDistinct = -1

            tuningShiftPairs.forEach { pair ->
                val random = HGTG42Random(System.nanoTime(), inc, pair)
                val avgDistinct = (1..8).sumOf { bitCount ->
                    val samples = IntArray(100_000) { random.nextBits(bitCount + 1) }
                    samples.distinct().size
                }
                logDebug("InternalTest", "Pair=$pair, AvgDistinct=$avgDistinct")
                if (avgDistinct > maxDistinct) {
                    maxDistinct = avgDistinct
                    bestPair = shiftPair.first to shiftPair.second
                }
            }
            return bestPair
        }
    }

    private fun safeShift(value: Long, shift: Int): Long = value ushr shift.coerceIn(0, 63)

    override fun nextBits(bitCount: Int): Int {
        require(bitCount in 1..32) { "bitCount must be between 1 and 32" }

        state = state * MULTIPLIER + i

        val tuningXorFirst = shiftPair.first
        val tuningXorSecond = shiftPair.first + 9
        val tuningXorMidFirst = tuningXorFirst + 5
        val tuningXorMidSecond = tuningXorSecond - 5

        val tuningRotFirst = shiftPair.second
        val tuningRotSecond = shiftPair.second - 37
        val tuningRotMidFirst = tuningRotFirst + 5
        val tuningRotMidSecond = tuningRotSecond - 5

        val xorState = state xor midFilter
        val xorshiftedLow = (safeShift(xorState, tuningXorFirst) xor safeShift(state, tuningXorSecond)) and lowerFilter
        val xorshiftedMid = (safeShift(xorState, tuningXorMidFirst) xor safeShift(state, tuningXorMidSecond)) and midFilter
        val xorshiftedHigh = (safeShift(xorState, tuningXorFirst) xor safeShift(state, tuningXorSecond)) and higherFilter

        val rotLow = (safeShift(state, tuningRotFirst) xor safeShift(lowerFilter, tuningRotSecond)).toInt()
        val rotMid = (safeShift(state, tuningRotMidFirst) xor safeShift(lowerFilter, tuningRotMidSecond)).toInt()
        val rotHigh = (safeShift(state, tuningRotFirst) xor safeShift(higherFilter, tuningRotSecond)).toInt()

        val mixLow = (xorshiftedLow ushr rotLow) or (xorshiftedLow shl ((-rotLow) and 31))
        val mixMid = (xorshiftedMid ushr rotMid) or (xorshiftedMid shl ((-rotMid) and 31))
        val mixHigh = (xorshiftedHigh ushr rotHigh) or (xorshiftedHigh shl ((-rotHigh) and 31))

        val result = mixLow xor mixMid xor mixHigh

        return if (bitCount == 32) result.toInt()
        else (result and (((1 shl bitCount) - 1).toLong())).toInt()
    }

    override fun nextInt(): Int = nextBits(32)
    override fun nextInt(until: Int): Int = nextInt(0, until)
    override fun nextInt(from: Int, until: Int): Int {
        require(from < until) { "until must be greater than from." }
        val range = until - from
        return from + (nextBits(31).absoluteValue % range).toInt()
    }

    override fun nextLong(): Long = (nextBits(32).toLong() shl 32) or nextBits(32).toLong()
    override fun nextBoolean(): Boolean = nextBits(1) != 0
    override fun nextDouble(): Double = nextBits(53).toLong() / (1L shl 53).toDouble()
    override fun nextDouble(from: Double, until: Double): Double {
        require(from < until) { "until must be greater than from." }
        return from + (until - from) * nextDouble()
    }

companion object {
    fun generateAllShiftPairs(
        minFirst: Int = 1,
        maxFirst: Int = 31,
        minSecond: Int = 1,
        maxSecond: Int = 63
    ): List<Pair<Int, Int>> {
        val pairs = mutableListOf<Pair<Int, Int>>()
        for (first in minFirst..maxFirst) {
            for (second in minSecond..maxSecond) {
                if (first != second) {
                    pairs.add(Pair(first, second))
                }
            }
        }
        return pairs
    }

    fun findBestShiftPair(seed: Long, inc: Long, sampleSize: Int = 1024): Pair<Int, Int> {
        var bestPair = Pair(0, 0)
        var bestScore = Double.MAX_VALUE

        for (pair in generateAllShiftPairs()) {
            val score = scoreShiftPair(seed, inc, pair, sampleSize)
            if (score < bestScore) {
                bestScore = score
                bestPair = pair
            }
        }

        return bestPair
    }

    fun scoreShiftPair(seed: Long, inc: Long, pair: Pair<Int, Int>, sampleSize: Int = 1024): Double {
        val rng = XPCGRandom(seed, inc, pair)
        var ones = 0

        repeat(sampleSize) {
            if (rng.nextBit()) ones++
        }

        val onesRatio = ones.toDouble() / sampleSize
        return kotlin.math.abs(0.5 - onesRatio)
    }

fun resolveShiftPair(
    requestedPair: Pair<Int, Int>,
    seed: Long,
    inc: Long
): Pair<Int, Int> {
    return when (requestedPair) {
        Pair(0, 0) -> Pair(18, 54) // use fixed default pair
        Pair(1, 1) -> findBestShiftPair(seed, inc) // dynamically tune best pair
        else      -> requestedPair // use as-is
    }
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
