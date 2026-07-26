package com.atruedev.kmpble.mesh.crypto

/**
 * Pure Kotlin ECDH over secp256r1 (NIST P-256) for KMP portability.
 *
 * Implements key generation and ECDH shared secret derivation without
 * platform crypto APIs. Used on iOS where CommonCrypto interop for
 * ECDH is unavailable, and as a portable fallback for all platforms.
 *
 * ## Field arithmetic
 *
 * 256-bit field elements are stored as 4 x 64-bit limbs (little-endian:
 * index 0 is least significant). Multiplication produces 8-limb products
 * which are reduced modulo the P-256 prime using Barrett reduction.
 *
 * ## Security note
 *
 * NOT constant-time. Acceptable for BLE Mesh provisioning where sessions
 * are local, short-lived, and user-mediated (OOB auth provides MITM
 * protection independently of timing).
 */
internal object P256Ecdh {

    // P-256 prime: p = 2^256 - 2^224 + 2^192 + 2^96 - 1
    private val P = fe(
        0xFFFFFFFFFFFFFFFFuL,
        0x00000000FFFFFFFFuL,
        0x0000000000000000uL,
        0xFFFFFFFF00000001uL,
    )

    // μ = floor(2^512 / p) for Barrett reduction
    private val MU = fe(
        0x0000000000000001uL,
        0xFFFFFFFF00000000uL,
        0xFFFFFFFFFFFFFFFFuL,
        0x00000000FFFFFFFEuL,
    )

    // Curve parameter a = -3 mod p
    private val A = fe(
        0xFFFFFFFFFFFFFFFCuL,
        0x00000000FFFFFFFFuL,
        0x0000000000000000uL,
        0xFFFFFFFF00000001uL,
    )

    // Curve parameter b
    private val B = fe(
        0x3BCE3C3E27D2604BuL,
        0x651D06B0CC53B0F6uL,
        0xB3EBBD55769886BCuL,
        0x5AC635D8AA3A93E7uL,
    )

    // Generator Gx
    private val GX = fe(
        0xF4A13945D898C296uL,
        0x77037D812DEB33A0uL,
        0xF8BCE6E563A440F2uL,
        0x6B17D1F2E12C4247uL,
    )

    // Generator Gy
    private val GY = fe(
        0xCBB6406837BF51F5uL,
        0x2BCE33576B315ECEuL,
        0x8EE7EB4A7C0F9E16uL,
        0x4FE342E2FE1A7F9BuL,
    )

    // Order n of the generator
    private val ORDER = fe(
        0xF3B9CAC2FC632551uL,
        0xBCE6FAADA7179E84uL,
        0xFFFFFFFF00000000uL,
        0xFFFFFFFF00000000uL,
    )

    // --- Public API ---

    fun generateKeyPair(): EcdhKeyPair {
        val privBytes = generatePrivateKey()
        val pubBytes = computePublicKey(privBytes)
        return EcdhKeyPair(privBytes, pubBytes)
    }

    fun sharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }
        require(publicKey.size == 64) { "Public key must be 64 bytes (X||Y)" }
        val scalar = bytesToFe(privateKey)
        val x = bytesToFe(publicKey.copyOfRange(0, 32))
        val y = bytesToFe(publicKey.copyOfRange(32, 64))
        val result = scalarMult(Point(x, y), scalar)
        return feToBytes(result.x)
    }

    // --- Internal ---

    private data class Point(val x: Fe, val y: Fe)

    private val INFINITY: Point = Point(LongArray(4), LongArray(4))

    private fun generatePrivateKey(): ByteArray {
        var bytes: ByteArray
        do {
            bytes = CryptoEngine.secureRandomBytes(32)
            bytes[0] = (bytes[0].toInt() and 0x7F).toByte() // clear top bit
        } while (isZero(bytesToFe(bytes)) || cmp(bytesToFe(bytes), ORDER) >= 0)
        return bytes
    }

    private fun computePublicKey(privBytes: ByteArray): ByteArray {
        val scalar = bytesToFe(privBytes)
        val pt = scalarMult(Point(GX, GY), scalar)
        return feToBytes(pt.x) + feToBytes(pt.y)
    }

    private fun scalarMult(pt: Point, scalar: Fe): Point {
        var result = INFINITY
        var addend = pt
        for (wordIdx in 3 downTo 0) {
            var w = scalar[wordIdx]
            for (bit in 0 until 64) {
                if ((w and 1L) != 0L) result = pointAdd(result, addend)
                addend = pointDouble(addend)
                w = w ushr 1
            }
        }
        return result
    }

    // --- Point operations ---

    private fun pointDouble(p: Point): Point {
        if (isZero(p)) return INFINITY
        // λ = (3x² + a) / (2y)  mod p
        val x2 = mul(p.x, p.x)
        val t3x2 = add(add(x2, x2), x2) // 3x²
        val num = add(t3x2, A)
        val den = add(p.y, p.y) // 2y
        val lambda = mul(num, inv(den))
        // x₃ = λ² - 2x
        val x3 = sub(mul(lambda, lambda), add(p.x, p.x))
        // y₃ = λ(x - x₃) - y
        val y3 = sub(mul(lambda, sub(p.x, x3)), p.y)
        return Point(x3, y3)
    }

    private fun pointAdd(p1: Point, p2: Point): Point {
        if (isZero(p1)) return p2
        if (isZero(p2)) return p1
        if (p1.x.contentEquals(p2.x)) {
            return if (p1.y.contentEquals(p2.y)) pointDouble(p1) else INFINITY
        }
        // λ = (y₂ - y₁) / (x₂ - x₁)  mod p
        val num = sub(p2.y, p1.y)
        val den = sub(p2.x, p1.x)
        val lambda = mul(num, inv(den))
        // x₃ = λ² - x₁ - x₂
        val x3 = sub(sub(mul(lambda, lambda), p1.x), p2.x)
        // y₃ = λ(x₁ - x₃) - y₁
        val y3 = sub(mul(lambda, sub(p1.x, x3)), p1.y)
        return Point(x3, y3)
    }

    // --- Field arithmetic mod P ---

    private typealias Fe = LongArray // 4 x 64-bit limbs, little-endian

    private fun fe(v0: ULong, v1: ULong, v2: ULong, v3: ULong): Fe =
        longArrayOf(v0.toLong(), v1.toLong(), v2.toLong(), v3.toLong())

    private fun add(a: Fe, b: Fe): Fe {
        val r = Fe(4)
        var carry = 0L
        for (i in 0..3) {
            val t = a[i] + b[i]
            val c1 = if (unsignedLess(t, a[i])) 1L else 0L
            val sum = t + carry
            val c2 = if (unsignedLess(sum, t)) 1L else 0L
            r[i] = sum
            carry = c1 + c2
        }
        if (carry != 0L || cmp(r, P) >= 0) {
            var borrow = 0L
            for (i in 0..3) {
                val d = r[i] - P[i] - borrow
                borrow = if (unsignedLess(r[i], P[i]) || (borrow != 0L && r[i] == P[i])) 1L else 0L
                r[i] = d
            }
        }
        return r
    }

    private fun sub(a: Fe, b: Fe): Fe {
        val r = Fe(4)
        var borrow = 0L
        for (i in 0..3) {
            r[i] = a[i] - b[i] - borrow
            borrow = if (unsignedLess(a[i], b[i]) || (borrow != 0L && a[i] == b[i])) 1L else 0L
        }
        if (borrow != 0L) {
            // add p back
            var carry = 0L
            for (i in 0..3) {
                val s = r[i] + P[i] + carry
                carry = if (unsignedLess(s, r[i]) || unsignedLess(s, P[i])) 1L else 0L
                r[i] = s
            }
        }
        return r
    }

    private fun mul(a: Fe, b: Fe): Fe {
        val prod = LongArray(8)
        for (i in 0..3) {
            var carry = 0L
            for (j in 0..3) {
                val (lo, hi) = umul64(a[i], b[j])
                val t1 = prod[i + j] + lo
                val c1 = if (unsignedLess(t1, prod[i + j])) 1L else 0L
                val t2 = t1 + carry
                val c2 = if (unsignedLess(t2, t1)) 1L else 0L
                prod[i + j] = t2
                carry = hi + c1 + c2
            }
            prod[i + 4] = carry
        }
        return barrettReduce(prod)
    }

    /** Barrett reduction: reduce 512-bit x to 256-bit result mod p. */
    private fun barrettReduce(x: LongArray): Fe {
        // Use Barrett estimation then subtract-and-adjust
        val xHi = x.copyOfRange(4, minOf(8, x.size))
        val q = mulHi(xHi, MU)

        // Compute q * P
        val qp = LongArray(8)
        for (i in 0..3) {
            var carry = 0L
            for (j in 0..3) {
                val (lo, hi) = umul64(q[i], P[j])
                val t1 = qp[i + j] + lo
                val c1 = if (unsignedLess(t1, qp[i + j])) 1L else 0L
                val t2 = t1 + carry
                val c2 = if (unsignedLess(t2, t1)) 1L else 0L
                qp[i + j] = t2
                carry = hi + c1 + c2
            }
            qp[i + 4] = carry
        }

        // r = x - q*p (subtract across all limbs)
        val r = x.copyOf(8)
        var borrow = 0L
        for (i in 0..7) {
            r[i] = r[i] - qp[i] - borrow
            borrow = if (unsignedLess(r[i] + qp[i] + borrow, qp[i]) ||
                (borrow != 0L && r[i] + qp[i] == qp[i])) 1L else 0L
            // Simpler borrow detection:
        }
        // Fixup: correct borrow and ensure r in [0, p)
        borrow = 0L
        for (i in 0..7) {
            val orig = if (i < x.size) x[i] else 0L
            r[i] = orig - qp[i] - borrow
            borrow = if (unsignedLess(orig - borrow, qp[i]) ||
                (borrow != 0L && orig == qp[i])) 1L else 0L
        }

        val result = r.copyOf(4)
        // If borrow is set, q was too high, add P back (at most once, since Barrett error is at most 1)
        while (borrow != 0L || cmp(result, P) >= 0) {
            var c = 0L
            for (i in 0..3) {
                val s = result[i] + P[i] + c
                val c1 = if (unsignedLess(s, result[i])) 1L else 0L
                val c2 = if (unsignedLess(s, P[i])) 1L else 0L
                result[i] = s
                c = c1 + c2
            }
            borrow = 0L
            // If we added P and still >= P, loop again
        }
        return result
    }

    /** Return high 256 bits of a*b (512-bit product). */
    private fun mulHi(a: Fe, b: Fe): Fe {
        val prod = LongArray(8)
        for (i in 0..3) {
            var carry = 0L
            for (j in 0..3) {
                val (lo, hi) = umul64(a[i], b[j])
                val t1 = prod[i + j] + lo
                val c1 = if (unsignedLess(t1, prod[i + j])) 1L else 0L
                val t2 = t1 + carry
                val c2 = if (unsignedLess(t2, t1)) 1L else 0L
                prod[i + j] = t2
                carry = hi + c1 + c2
            }
            prod[i + 4] = carry
        }
        return prod.copyOfRange(4, 8)
    }

    /** Return a^(-1) mod p using Fermat's little theorem: a^(p-2). */
    private fun inv(a: Fe): Fe {
        // p - 2: subtract 2 from P
        val exp = P.copyOf(4)
        exp[0] = P[0] - 2L // P[0] = 0xFFFFFFFFFFFFFFFFuL, minus 2 = 0xFFFFFFFFFFFFFFFD
        var result = fe(1uL, 0uL, 0uL, 0uL)
        var base = a.copyOf(4)
        for (wordIdx in 3 downTo 0) {
            var w = exp[wordIdx]
            for (bit in 0 until 64) {
                if ((w and 1L) != 0L) {
                    result = mul(result, base)
                }
                base = mul(base, base)
                w = w ushr 1
            }
        }
        return result
    }

    // --- Comparison ---

    private fun cmp(a: Fe, b: Fe): Int {
        for (i in 3 downTo 0) {
            if (a[i] == b[i]) continue
            return if (unsignedLess(a[i], b[i])) -1 else 1
        }
        return 0
    }

    private fun isZero(a: Fe): Boolean = a.all { it == 0L }
    private fun isZero(pt: Point): Boolean = isZero(pt.x) && isZero(pt.y)

    // --- Unsigned 64-bit helpers ---

    /** Unsigned comparison: true if a < b (treating both as unsigned). */
    private fun unsignedLess(a: Long, b: Long): Boolean =
        (a xor Long.MIN_VALUE) < (b xor Long.MIN_VALUE)

    /** Unsigned 64-bit multiply: returns (low64, high64). */
    private fun umul64(a: Long, b: Long): Pair<Long, Long> {
        val aHi = a ushr 32
        val aLo = a and 0xFFFFFFFFL
        val bHi = b ushr 32
        val bLo = b and 0xFFFFFFFFL
        val lo = aLo * bLo
        val mid1 = aHi * bLo
        val mid2 = aLo * bHi
        val hi = aHi * bHi
        val mid = mid1 + mid2
        val midCarry = if (unsignedLess(mid, mid1)) 1L else 0L
        val lowResult = lo + (mid shl 32)
        val lowCarry = if (unsignedLess(lowResult, lo)) 1L else 0L
        val highResult = hi + (mid ushr 32) + midCarry + lowCarry
        return Pair(lowResult, highResult)
    }

    // --- Byte conversion (big-endian ← → little-endian limbs) ---

    private fun bytesToFe(bytes: ByteArray): Fe {
        val r = Fe(4)
        for (limbIdx in 0..3) {
            var v = 0L
            for (b in 0..7) {
                // Read bytes big-endian: limb 0 = bytes[0..7] (most significant)
                v = (v shl 8) or (bytes[limbIdx * 8 + b].toLong() and 0xFF)
            }
            // Store: r[3] = most significant limb, r[0] = least significant
            r[3 - limbIdx] = v
        }
        return r
    }

    private fun feToBytes(fe: Fe): ByteArray {
        val bytes = ByteArray(32)
        for (limbIdx in 0..3) {
            var v = fe[3 - limbIdx]
            for (b in 7 downTo 0) {
                bytes[limbIdx * 8 + b] = (v and 0xFF).toByte()
                v = v ushr 8
            }
        }
        return bytes
    }
}
