@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.hengji.data

import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataGetTypeID
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFGetTypeID
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val HENGJI_DATABASE_KEY_SERVICE = "com.hengji.database-key.v1"

/**
 * iOS database-key provider backed by a non-synchronizing, device-only Keychain generic-password item.
 *
 * The key is available only while the device is unlocked and is not migrated to another device. An unexpected
 * Keychain status fails closed; duplicate first creation reloads the winning item instead of replacing it.
 */
class IosKeychainDatabaseKeyProvider : ProvisioningDatabaseKeyProvider {
    override suspend fun loadKey(alias: String): DatabaseKeyMaterial? = withContext(Dispatchers.Default) {
        requireValidDatabaseKeyAlias(alias)
        copyKey(alias)?.let { rawKey ->
            try {
                DatabaseKeyMaterial(rawKey)
            } finally {
                rawKey.fill(0)
            }
        }
    }

    override suspend fun loadOrCreateKey(alias: String): DatabaseKeyMaterial = withContext(Dispatchers.Default) {
        requireValidDatabaseKeyAlias(alias)
        loadKey(alias)?.let { return@withContext it }

        val rawKey = ByteArray(AES_256_KEY_BYTES)
        val randomStatus = rawKey.usePinned { pinned ->
            SecRandomCopyBytes(
                rnd = kSecRandomDefault,
                count = rawKey.size.toULong(),
                bytes = pinned.addressOf(0),
            )
        }
        if (randomStatus != errSecSuccess) {
            rawKey.fill(0)
            throw StorageProtectionException("Apple secure random generation failed with status $randomStatus")
        }

        try {
            when (val status = addKey(alias, rawKey)) {
                errSecSuccess -> DatabaseKeyMaterial(rawKey)
                errSecDuplicateItem -> loadKey(alias)
                    ?: throw StorageProtectionException(
                        "Apple Keychain reported a duplicate database key but no item is readable",
                    )
                else -> throw StorageProtectionException(
                    "Apple Keychain could not store the database key; status=$status",
                )
            }
        } finally {
            rawKey.fill(0)
        }
    }

    private fun copyKey(alias: String): ByteArray? = withBaseQuery(alias) { query ->
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<COpaquePointerVar>()
            result.value = null
            when (val status = SecItemCopyMatching(query, result.ptr)) {
                errSecItemNotFound -> null
                errSecSuccess -> {
                    val value = result.value
                        ?: throw StorageProtectionException("Apple Keychain returned an empty database key")
                    try {
                        if (CFGetTypeID(value) != CFDataGetTypeID()) {
                            throw StorageProtectionException("Apple Keychain returned an unexpected item type")
                        }
                        val length = CFDataGetLength(value.reinterpret())
                        if (length != AES_256_KEY_BYTES.toLong()) {
                            throw StorageProtectionException("Apple Keychain database key has an invalid size")
                        }
                        val bytes = CFDataGetBytePtr(value.reinterpret())
                            ?: throw StorageProtectionException("Apple Keychain returned unreadable key data")
                        ByteArray(AES_256_KEY_BYTES) { index -> bytes[index].toByte() }
                    } finally {
                        CFRelease(value)
                    }
                }
                else -> throw StorageProtectionException(
                    "Apple Keychain could not read the database key; status=$status",
                )
            }
        }
    }

    private fun addKey(alias: String, rawKey: ByteArray): Int = withBaseQuery(alias) { query ->
        val data = rawKey.usePinned { pinned ->
            CFDataCreate(
                allocator = kCFAllocatorDefault,
                bytes = pinned.addressOf(0).reinterpret(),
                length = rawKey.size.toLong(),
            )
        } ?: throw StorageProtectionException("Apple Keychain key data could not be allocated")
        try {
            CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
            CFDictionarySetValue(query, kSecValueData, data)
            SecItemAdd(query, null)
        } finally {
            CFRelease(data)
        }
    }
}

private inline fun <T> withBaseQuery(
    alias: String,
    block: (platform.CoreFoundation.CFMutableDictionaryRef) -> T,
): T = memScoped {
    val aliasString = CFStringCreateWithCString(
        alloc = kCFAllocatorDefault,
        cStr = alias,
        encoding = kCFStringEncodingUTF8,
    ) ?: throw StorageProtectionException("Apple Keychain alias could not be encoded")
    val serviceString = CFStringCreateWithCString(
        alloc = kCFAllocatorDefault,
        cStr = HENGJI_DATABASE_KEY_SERVICE,
        encoding = kCFStringEncodingUTF8,
    ) ?: run {
        CFRelease(aliasString)
        throw StorageProtectionException("Apple Keychain service could not be encoded")
    }
    val query = CFDictionaryCreateMutable(
        allocator = kCFAllocatorDefault,
        capacity = 8,
        keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
        valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
    ) ?: run {
        CFRelease(serviceString)
        CFRelease(aliasString)
        throw StorageProtectionException("Apple Keychain query could not be allocated")
    }
    try {
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValue(query, kSecAttrService, serviceString)
        CFDictionarySetValue(query, kSecAttrAccount, aliasString)
        CFDictionarySetValue(query, kSecAttrSynchronizable, kCFBooleanFalse)
        block(query)
    } finally {
        CFRelease(query)
        CFRelease(serviceString)
        CFRelease(aliasString)
    }
}
