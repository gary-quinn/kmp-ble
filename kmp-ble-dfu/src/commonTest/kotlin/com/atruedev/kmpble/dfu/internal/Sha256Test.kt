package com.atruedev.kmpble.dfu.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {

    @Test
    fun emptyInput() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.digestHex(byteArrayOf()),
        )
    }

    @Test
    fun singleCharacter() {
        assertEquals(
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            Sha256.digestHex("a".encodeToByteArray()),
        )
    }

    @Test
    fun abc() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.digestHex("abc".encodeToByteArray()),
        )
    }

    @Test
    fun nistTwoBlockMessage() {
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.digestHex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()),
        )
    }

    @Test
    fun digits123456789() {
        assertEquals(
            "15e2b0d3c33891ebb0f1ef609ec419420c20e320ce94c65fbc8c3312448eb225",
            Sha256.digestHex("123456789".encodeToByteArray()),
        )
    }

    @Test
    fun exactBlockBoundary() {
        // 55 bytes + 1 byte padding + 8 bytes length = 64 bytes = 1 block
        val data = ByteArray(55) { 0x41 } // 55 'A's
        val hex = Sha256.digestHex(data)
        assertEquals(64, hex.length) // valid 256-bit hash
    }

    @Test
    fun digestReturnsByteArray() {
        val result = Sha256.digest("abc".encodeToByteArray())
        assertEquals(32, result.size)
    }
}
