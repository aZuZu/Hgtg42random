HGTG42Random - Semi-Automatic Tunable PRNG

HGTG42Random is a Kotlin-native pseudo-random number generator inspired by PCG, enhanced with dynamic shift-pair tuning and entropy control. Designed for high-quality randomness, calibration, and reproducibility, this PRNG offers advanced configurability without sacrificing performance.


---

Features

Shift-Pair Tunable Core: Custom bit permutation via (shift1, shift2) pairs

Semi-Automatic Mode:

Pair(0, 0): Use default shift pair (18, 54)

Pair(1, 1): Automatically tune the best shift pair for given seed & increment

Any other pair is treated as manually tuned and used directly


Entropy Evaluation using nextBit()

Kotlin-native, lightweight and self-contained

Perfect for simulations, procedural generation, and lottery-style tuning



---

Usage

// Default shift pair
val rng1 = HGTG42Random(seed = 1234L, inc = 5678L, pair = Pair(0, 0))

// Auto-tuned best shift pair
val rng2 = HGTG42Random(seed = 1234L, inc = 5678L, pair = Pair(1, 1))

// Manually tuned shift pair
val rng3 = HGTG42Random(seed = 1234L, inc = 5678L, pair = Pair(17, 44))

val randomValue = rng3.nextInt()


---

How it Works

Internally, HGTG42Random mutates its internal state similarly to PCG but adds:

Tunable shift-based bit mixing via (first, second) pair

resolveShiftPair() that decides how to interpret the shift pair

Optional scanning of all valid shift pairs to find the best entropy balance using nextBit()



---

Shift Pair Logic

Pair(0, 0): Uses safe, hardcoded default: (18, 54)

Pair(1, 1): Triggers automatic evaluation across all valid shift pairs

All other values: Used directly, assuming you have already tuned them externally



---

Performance

HGTG42Random is competitive with LMK and faster than complex RNGs like MT19937, especially when tuned correctly. Typical tuning runs scan 1,000+ seeds and evaluate bit balance in milliseconds on modern hardware.


---

Why HGTG42?

HGTG: Hitchhiker's Guide to the Galaxy meets PRNG theory

42: Because 42 is always the answer

Built with love for entropy, tuning, and Kotlin simplicity



---

License

MIT License — use freely, credit appreciated!


---

Author
Ivan Slaninka

---

Contributions

Ideas? Improvements? PRs welcome.


---

Coming Soon

Benchmarking suite

CSV/JSON seed result dump

Visual entropy heatmaps

Integration with Kotlin Random
