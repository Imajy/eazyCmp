package com.aj.shared.parser

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA512
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA512_DIGEST_LENGTH
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCBlockSizeAES128
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCSuccess
import platform.posix.size_tVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value

@OptIn(ExperimentalForeignApi::class)
internal actual object PlatformCrypto {
    actual fun sha1(data: ByteArray): ByteArray {
        val out = ByteArray(CC_SHA1_DIGEST_LENGTH)
        data.usePinned { inp ->
            out.usePinned { o ->
                CC_SHA1(inp.addressOf(0), data.size.convert(), o.addressOf(0))
            }
        }
        return out
    }

    actual fun sha256(data: ByteArray): ByteArray {
        val out = ByteArray(CC_SHA256_DIGEST_LENGTH)
        data.usePinned { inp ->
            out.usePinned { o ->
                CC_SHA256(inp.addressOf(0), data.size.convert(), o.addressOf(0))
            }
        }
        return out
    }

    actual fun sha512(data: ByteArray): ByteArray {
        val out = ByteArray(CC_SHA512_DIGEST_LENGTH)
        data.usePinned { inp ->
            out.usePinned { o ->
                CC_SHA512(inp.addressOf(0), data.size.convert(), o.addressOf(0))
            }
        }
        return out
    }

    actual fun aesCbcDecrypt(key: ByteArray, iv: ByteArray, cipherText: ByteArray): ByteArray {
        // CommonCrypto CBC (default when ECB flag not set)
        val out = ByteArray(cipherText.size + kCCBlockSizeAES128.toInt())
        memScoped {
            val moved = alloc<size_tVar>()
            val status = key.usePinned { k ->
                iv.usePinned { ivp ->
                    cipherText.usePinned { c ->
                        out.usePinned { o ->
                            CCCrypt(
                                kCCDecrypt,
                                kCCAlgorithmAES,
                                0u, // CBC, no PKCS7 — OOXML uses NoPadding
                                k.addressOf(0),
                                key.size.convert(),
                                ivp.addressOf(0),
                                c.addressOf(0),
                                cipherText.size.convert(),
                                o.addressOf(0),
                                out.size.convert(),
                                moved.ptr,
                            )
                        }
                    }
                }
            }
            if (status != kCCSuccess) {
                throw IllegalStateException("AES decrypt failed: $status")
            }
            return out.copyOf(moved.value.toInt())
        }
    }
}
