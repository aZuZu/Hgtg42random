HGTG42Random - Semi-Automatic Tunable PRNG

HGTG42Random is a Kotlin-native pseudo-random number generator inspired by PCG, enhanced with dynamic shift-pair tuning and entropy control. Designed for high-quality randomness, calibration, and reproducibility, this PRNG offers advanced configurability without sacrificing performance.


---

Features

Internal calibration using Top10 Pairs based on full specter scanning from 1-62

Entropy Evaluation using nextBit()

Kotlin-native, lightweight and self-contained

Perfect for simulations, procedural generation, and lottery-style tuning



---

Usage

val rng1 = HGTG42Random(seed = 1234L, inc = 5678L)

val randomValue = rng3.nextInt()


---

How it Works

Internally, HGTG42Random mutates its internal state similarly to PCG but adds:


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
