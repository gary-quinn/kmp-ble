package com.atruedev.kmpble.dfu.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class Md5Test {

    @Test
    fun emptyInput() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Md5.digestHex(byteArrayOf()))
    }

    @Test
    fun singleCharacterA() {
        assertEquals("0cc175b9c0f1b6a831c399e269772661", Md5.digestHex("a".encodeToByteArray()))
    }

    @Test
    fun abc() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Md5.digestHex("abc".encodeToByteArray()))
    }

    @Test
    fun messageDigest() {
        assertEquals(
            "f96b697d7cb7938d525a2f31aaf161d0",
            Md5.digestHex("message digest".encodeToByteArray()),
        )
    }

    @Test
    fun alphabetLowercase() {
        assertEquals(
            "c3fcd3d76192e4007dfb496cca67e13b",
            Md5.digestHex("abcdefghijklmnopqrstuvwxyz".encodeToByteArray()),
        )
    }

    @Test
    fun digits123456789() {
        assertEquals(
            "25f9e794323b453885f5181f1b624d0b",
            Md5.digestHex("123456789".encodeToByteArray()),
        )
    }

    @Test
    fun digestReturnsByteArray() {
        val result = Md5.digest("abc".encodeToByteArray())
        assertEquals(16, result.size)
    }
}
