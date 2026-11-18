package com.aj.shared.parser

/**
 * Decrypt password-protected OOXML (Agile Encryption / MS-OFFCRYPTO) using platform crypto.
 * Returns the inner ZIP bytes of the workbook, or null if not encrypted / wrong password.
 */
internal object OoxmlAgileDecrypt {

    private val BLOCK_KEY_VERIFIER_INPUT =
        byteArrayOf(0xfe.toByte(), 0xa7.toByte(), 0xd2.toByte(), 0x76, 0x3b, 0x4b, 0x9e.toByte(), 0x79)
    private val BLOCK_KEY_VERIFIER_VALUE =
        byteArrayOf(0xd7.toByte(), 0xaa.toByte(), 0x0f, 0x6d, 0x30, 0x61, 0x34, 0x4e)
    private val BLOCK_KEY_SECRET =
        byteArrayOf(0x14, 0x6e, 0x0b, 0xe7.toByte(), 0xab.toByte(), 0xac.toByte(), 0xd0.toByte(), 0xd6.toByte())

    fun tryDecrypt(oleBytes: ByteArray, password: String): ByteArray? {
        if (password.isEmpty()) return null
        if (!looksLikeOle(oleBytes)) return null
        val streams = try {
            OleCompoundFile.readStreams(oleBytes)
        } catch (_: Throwable) {
            return null
        }
        val encInfo = streams.entries.firstOrNull { (n, _) ->
            n.trim('\u0000').equals("EncryptionInfo", true)
        }?.value ?: return null
        val encPkg = streams.entries.firstOrNull { (n, _) ->
            n.trim('\u0000').equals("EncryptedPackage", true)
        }?.value ?: return null
        if (encInfo.size < 8) return null

        // Agile: version major/minor often 4.4 / 4.3; XML follows after 8-byte header.
        val major = encInfo.readU16(0)
        val minor = encInfo.readU16(2)
        val xmlStart = if (major == 4 || minor >= 3) 8 else 8
        val xml = encInfo.copyOfRange(xmlStart.coerceAtMost(encInfo.size), encInfo.size).decodeToString()
        if (!xml.contains("keyData", ignoreCase = true)) {
            throw EncryptedWorkbookException(
                "Encrypted workbook uses an unsupported encryption scheme " +
                    "(need Agile Encryption). Provide FileParseOptions.password or remove the password in Excel.",
            )
        }

        val keyDataSalt = attrB64(xml, "keyData", "saltValue")
            ?: throw EncryptedWorkbookException("EncryptionInfo missing keyData saltValue")
        val keyDataHash = attr(xml, "keyData", "hashAlgorithm") ?: "SHA512"
        val keyDataBlock = attr(xml, "keyData", "blockSize")?.toIntOrNull() ?: 16
        val keyDataKeyBits = attr(xml, "keyData", "keyBits")?.toIntOrNull() ?: 256

        // password key encryptor (p:encryptedKey / encryptedKey)
        val pke = extractPasswordKeyEncryptor(xml)
            ?: throw EncryptedWorkbookException("EncryptionInfo missing password key encryptor")

        val hashAlg = pke.hashAlgorithm
        val spin = pke.spinCount
        val salt = pke.saltValue
        val keyBits = pke.keyBits
        val blockSize = pke.blockSize

        val pwHash = hashPassword(password, salt, spin, hashAlg)
        // Verify password
        val verifierKey = generateKey(pwHash, BLOCK_KEY_VERIFIER_INPUT, keyBits / 8, hashAlg)
        val verifierIv = generateIv(salt, null, blockSize, hashAlg)
        val verifierInput = aesDecryptPadded(verifierKey, verifierIv, pke.encryptedVerifierHashInput)
        val verifierValueKey = generateKey(pwHash, BLOCK_KEY_VERIFIER_VALUE, keyBits / 8, hashAlg)
        val verifierValueIv = generateIv(salt, null, blockSize, hashAlg)
        val decryptedHash = aesDecryptPadded(verifierValueKey, verifierValueIv, pke.encryptedVerifierHashValue)
        val expected = hash(verifierInput.copyOf(pke.saltSize.coerceAtMost(verifierInput.size)), hashAlg)
        // Compare prefix of decrypted hash with expected (hashSize bytes)
        val hashSize = pke.hashSize.coerceAtMost(expected.size).coerceAtMost(decryptedHash.size)
        if (!expected.copyOf(hashSize).contentEquals(decryptedHash.copyOf(hashSize))) {
            throw EncryptedWorkbookException("Wrong password for encrypted workbook.")
        }

        val secretKey = generateKey(pwHash, BLOCK_KEY_SECRET, keyBits / 8, hashAlg)
        val secretIv = generateIv(salt, null, blockSize, hashAlg)
        val intermediateKey = aesDecryptPadded(secretKey, secretIv, pke.encryptedKeyValue)
            .copyOf(keyDataKeyBits / 8)

        return decryptPackage(encPkg, intermediateKey, keyDataSalt, keyDataHash, keyDataBlock)
    }

    fun isEncryptedOle(bytes: ByteArray): Boolean {
        if (!looksLikeOle(bytes)) return false
        return try {
            val streams = OleCompoundFile.readStreams(bytes)
            streams.keys.any { it.trim('\u0000').equals("EncryptedPackage", true) } ||
                streams.keys.any { it.trim('\u0000').equals("EncryptionInfo", true) }
        } catch (_: Throwable) {
            false
        }
    }

    private fun decryptPackage(
        encPkg: ByteArray,
        key: ByteArray,
        salt: ByteArray,
        hashAlg: String,
        blockSize: Int,
    ): ByteArray {
        require(encPkg.size >= 8) { "EncryptedPackage too small" }
        val streamSize = encPkg.readI64(0)
        val encrypted = encPkg.copyOfRange(8, encPkg.size)
        val out = ByteArray(streamSize.toInt().coerceAtLeast(0).coerceAtMost(encrypted.size))
        var offset = 0
        var seg = 0
        while (offset < encrypted.size && offset < out.size) {
            val end = minOf(offset + 4096, encrypted.size)
            var chunk = encrypted.copyOfRange(offset, end)
            // pad to block
            val pad = (blockSize - (chunk.size % blockSize)) % blockSize
            if (pad > 0) chunk = chunk + ByteArray(pad)
            val iv = generateIv(salt, intLe(seg), blockSize, hashAlg)
            val plain = PlatformCrypto.aesCbcDecrypt(key, iv, chunk)
            val copyLen = minOf(plain.size, out.size - offset, 4096)
            plain.copyInto(out, offset, 0, copyLen)
            offset += 4096
            seg++
        }
        return out
    }

    private fun hashPassword(password: String, salt: ByteArray, spinCount: Int, hashAlg: String): ByteArray {
        val passBytes = password.encodeToByteArrayUtf16Le()
        var hash = hash(salt + passBytes, hashAlg)
        for (i in 0 until spinCount) {
            hash = hash(intLe(i) + hash, hashAlg)
        }
        return hash
    }

    private fun generateKey(
        hash: ByteArray,
        blockKey: ByteArray,
        keySize: Int,
        hashAlg: String,
    ): ByteArray {
        val h = hash(hash + blockKey, hashAlg)
        return when {
            h.size == keySize -> h
            h.size > keySize -> h.copyOf(keySize)
            else -> h + ByteArray(keySize - h.size) { 0x36 }
        }
    }

    private fun generateIv(
        salt: ByteArray,
        blockKey: ByteArray?,
        blockSize: Int,
        hashAlg: String,
    ): ByteArray {
        val h = if (blockKey == null) hash(salt, hashAlg) else hash(salt + blockKey, hashAlg)
        return when {
            h.size == blockSize -> h
            h.size > blockSize -> h.copyOf(blockSize)
            else -> h + ByteArray(blockSize - h.size) { 0x36 }
        }
    }

    private fun aesDecryptPadded(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        var input = data
        val block = 16
        val pad = (block - (input.size % block)) % block
        if (pad > 0) input = input + ByteArray(pad)
        return PlatformCrypto.aesCbcDecrypt(key, iv, input)
    }

    private fun hash(data: ByteArray, alg: String): ByteArray = when {
        alg.contains("SHA512", true) || alg.contains("SHA-512", true) -> PlatformCrypto.sha512(data)
        alg.contains("SHA256", true) || alg.contains("SHA-256", true) -> PlatformCrypto.sha256(data)
        else -> PlatformCrypto.sha1(data)
    }

    private data class Pke(
        val saltSize: Int,
        val blockSize: Int,
        val keyBits: Int,
        val hashSize: Int,
        val hashAlgorithm: String,
        val saltValue: ByteArray,
        val spinCount: Int,
        val encryptedVerifierHashInput: ByteArray,
        val encryptedVerifierHashValue: ByteArray,
        val encryptedKeyValue: ByteArray,
    )

    private fun extractPasswordKeyEncryptor(xml: String): Pke? {
        // Match either encryptedKey or p:encryptedKey / keyEncryptor password attrs
        val open = Regex(
            """<(?:(?:\w+:)?encryptedKey|(?:\w+:)?keyEncryptor)[^>]*>""",
            RegexOption.IGNORE_CASE,
        ).find(xml)?.value
            ?: Regex("""saltValue="[^"]+"[^>]*spinCount="\d+"""", RegexOption.IGNORE_CASE)
                .find(xml)?.value?.let { "<x $it>" }
            ?: return null
        fun a(name: String): String? =
            Regex("""$name="([^"]*)"""", RegexOption.IGNORE_CASE).find(open)?.groupValues?.get(1)
                ?: Regex("""$name="([^"]*)"""", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)
        val saltB64 = a("saltValue") ?: return null
        return Pke(
            saltSize = a("saltSize")?.toIntOrNull() ?: 16,
            blockSize = a("blockSize")?.toIntOrNull() ?: 16,
            keyBits = a("keyBits")?.toIntOrNull() ?: 256,
            hashSize = a("hashSize")?.toIntOrNull() ?: 64,
            hashAlgorithm = a("hashAlgorithm") ?: "SHA512",
            saltValue = SimpleBase64.decode(saltB64),
            spinCount = a("spinCount")?.toIntOrNull() ?: 100_000,
            encryptedVerifierHashInput = SimpleBase64.decode(a("encryptedVerifierHashInput") ?: return null),
            encryptedVerifierHashValue = SimpleBase64.decode(a("encryptedVerifierHashValue") ?: return null),
            encryptedKeyValue = SimpleBase64.decode(a("encryptedKeyValue") ?: return null),
        )
    }

    private fun attr(xml: String, tag: String, name: String): String? {
        val block = Regex("""<$tag\b[^>]*>""", RegexOption.IGNORE_CASE).find(xml)?.value ?: return null
        return Regex("""$name="([^"]*)"""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)
    }

    private fun attrB64(xml: String, tag: String, name: String): ByteArray? =
        attr(xml, tag, name)?.let { SimpleBase64.decode(it) }

    private fun looksLikeOle(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()

    private fun intLe(v: Int): ByteArray = byteArrayOf(
        (v and 0xff).toByte(),
        ((v shr 8) and 0xff).toByte(),
        ((v shr 16) and 0xff).toByte(),
        ((v shr 24) and 0xff).toByte(),
    )

    private fun String.encodeToByteArrayUtf16Le(): ByteArray {
        val out = ByteArray(length * 2)
        for (i in indices) {
            val c = this[i].code
            out[i * 2] = (c and 0xff).toByte()
            out[i * 2 + 1] = ((c shr 8) and 0xff).toByte()
        }
        return out
    }

    private fun ByteArray.readU16(i: Int): Int =
        (this[i].toInt() and 0xff) or ((this[i + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.readI64(i: Int): Long {
        var v = 0L
        for (k in 0 until 8) v = v or ((this[i + k].toLong() and 0xff) shl (8 * k))
        return v
    }
}
