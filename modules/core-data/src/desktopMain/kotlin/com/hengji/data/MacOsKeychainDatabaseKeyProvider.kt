package com.hengji.data

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.platform.mac.CoreFoundation
import com.sun.jna.platform.mac.CoreFoundation.CFBooleanRef
import com.sun.jna.platform.mac.CoreFoundation.CFDataRef
import com.sun.jna.platform.mac.CoreFoundation.CFDictionaryRef
import com.sun.jna.platform.mac.CoreFoundation.CFIndex
import com.sun.jna.platform.mac.CoreFoundation.CFMutableDictionaryRef
import com.sun.jna.platform.mac.CoreFoundation.CFStringRef
import com.sun.jna.platform.mac.CoreFoundation.CFTypeRef
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MACOS_KEYCHAIN_SERVICE = "com.hengji.database-key.v1"
private const val ERR_SEC_SUCCESS = 0
private const val ERR_SEC_DUPLICATE_ITEM = -25299
private const val ERR_SEC_ITEM_NOT_FOUND = -25300

/**
 * macOS database-key provider backed by a non-synchronizing data-protection Keychain item.
 *
 * The item uses `WhenUnlockedThisDeviceOnly`, so it cannot migrate to another device. Unexpected Security.framework
 * statuses fail closed; a duplicate first creation reloads the winning item and never overwrites it.
 */
class MacOsKeychainDatabaseKeyProvider : ProvisioningDatabaseKeyProvider {
    init {
        require(System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
            "macOS Keychain provider can only run on macOS"
        }
    }

    override suspend fun loadKey(alias: String): DatabaseKeyMaterial? = withContext(Dispatchers.IO) {
        requireValidDatabaseKeyAlias(alias)
        try {
            MacOsKeychainBridge.copyKey(alias)?.let { rawKey ->
                try {
                    DatabaseKeyMaterial(rawKey)
                } finally {
                    rawKey.fill(0)
                }
            }
        } catch (error: LinkageError) {
            throw StorageProtectionException("macOS Keychain native boundary is unavailable", error)
        }
    }

    override suspend fun loadOrCreateKey(alias: String): DatabaseKeyMaterial = withContext(Dispatchers.IO) {
        requireValidDatabaseKeyAlias(alias)
        loadKey(alias)?.let { return@withContext it }
        try {
            val rawKey = MacOsKeychainBridge.secureRandomKey()
            try {
                when (val status = MacOsKeychainBridge.addKey(alias, rawKey)) {
                    ERR_SEC_SUCCESS -> DatabaseKeyMaterial(rawKey)
                    ERR_SEC_DUPLICATE_ITEM -> loadKey(alias)
                        ?: throw StorageProtectionException(
                            "macOS Keychain reported a duplicate database key but no item is readable",
                        )
                    else -> throw StorageProtectionException(
                        "macOS Keychain could not store the database key; status=$status",
                    )
                }
            } finally {
                rawKey.fill(0)
            }
        } catch (error: LinkageError) {
            throw StorageProtectionException("macOS Keychain native boundary is unavailable", error)
        }
    }
}

private object MacOsKeychainBridge {
    private val core = CoreFoundation.INSTANCE
    private val security = Native.load("Security", MacSecurityLibrary::class.java)
    private val securitySymbols = NativeLibrary.getInstance("Security")
    private val coreSymbols = NativeLibrary.getInstance("CoreFoundation")

    private val kSecClass = securityConstant("kSecClass")
    private val kSecClassGenericPassword = securityConstant("kSecClassGenericPassword")
    private val kSecAttrService = securityConstant("kSecAttrService")
    private val kSecAttrAccount = securityConstant("kSecAttrAccount")
    private val kSecAttrSynchronizable = securityConstant("kSecAttrSynchronizable")
    private val kSecAttrAccessible = securityConstant("kSecAttrAccessible")
    private val kSecAttrAccessibleWhenUnlockedThisDeviceOnly =
        securityConstant("kSecAttrAccessibleWhenUnlockedThisDeviceOnly")
    private val kSecUseDataProtectionKeychain = securityConstant("kSecUseDataProtectionKeychain")
    private val kSecReturnData = securityConstant("kSecReturnData")
    private val kSecMatchLimit = securityConstant("kSecMatchLimit")
    private val kSecMatchLimitOne = securityConstant("kSecMatchLimitOne")
    private val kSecValueData = securityConstant("kSecValueData")
    private val kCFBooleanTrue = coreBooleanConstant("kCFBooleanTrue")
    private val kCFBooleanFalse = coreBooleanConstant("kCFBooleanFalse")
    private val dictionaryKeyCallbacks = coreSymbols.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks")
    private val dictionaryValueCallbacks = coreSymbols.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks")

    fun secureRandomKey(): ByteArray {
        val rawKey = ByteArray(AES_256_KEY_BYTES)
        val native = Memory(rawKey.size.toLong())
        try {
            val status = security.SecRandomCopyBytes(
                random = Pointer.NULL,
                count = NativeLong(rawKey.size.toLong()),
                bytes = native,
            )
            if (status != ERR_SEC_SUCCESS) {
                throw StorageProtectionException("Apple secure random generation failed with status $status")
            }
            native.read(0, rawKey, 0, rawKey.size)
            return rawKey
        } catch (error: Exception) {
            rawKey.fill(0)
            throw error
        } finally {
            native.clear()
            native.close()
        }
    }

    fun copyKey(alias: String): ByteArray? = withBaseQuery(alias) { query ->
        query.setValue(kSecReturnData, kCFBooleanTrue)
        query.setValue(kSecMatchLimit, kSecMatchLimitOne)
        val result = PointerByReference()
        when (val status = security.SecItemCopyMatching(query, result)) {
            ERR_SEC_ITEM_NOT_FOUND -> null
            ERR_SEC_SUCCESS -> {
                val pointer = result.value
                    ?: throw StorageProtectionException("macOS Keychain returned an empty database key")
                val value = CFTypeRef(pointer)
                try {
                    if (!value.isTypeID(CoreFoundation.DATA_TYPE_ID)) {
                        throw StorageProtectionException("macOS Keychain returned an unexpected item type")
                    }
                    val data = CFDataRef(pointer)
                    val length = data.length
                    if (length != AES_256_KEY_BYTES) {
                        throw StorageProtectionException("macOS Keychain database key has an invalid size")
                    }
                    data.bytePtr.getByteArray(0, length)
                } finally {
                    value.release()
                }
            }
            else -> throw StorageProtectionException(
                "macOS Keychain could not read the database key; status=$status",
            )
        }
    }

    fun addKey(alias: String, rawKey: ByteArray): Int = withBaseQuery(alias) { query ->
        val native = Memory(rawKey.size.toLong())
        try {
            native.write(0, rawKey, 0, rawKey.size)
            val data = core.CFDataCreate(null, native, CFIndex(rawKey.size.toLong()))
                ?: throw StorageProtectionException("macOS Keychain key data could not be allocated")
            try {
                query.setValue(kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
                query.setValue(kSecValueData, data)
                security.SecItemAdd(query, null)
            } finally {
                data.release()
            }
        } finally {
            native.clear()
            native.close()
        }
    }

    private inline fun <T> withBaseQuery(alias: String, block: (CFMutableDictionaryRef) -> T): T {
        val aliasString = CFStringRef.createCFString(alias)
        val serviceString = CFStringRef.createCFString(MACOS_KEYCHAIN_SERVICE)
        val query = core.CFDictionaryCreateMutable(
            null,
            CFIndex(8),
            dictionaryKeyCallbacks,
            dictionaryValueCallbacks,
        ) ?: run {
            serviceString.release()
            aliasString.release()
            throw StorageProtectionException("macOS Keychain query could not be allocated")
        }
        try {
            query.setValue(kSecClass, kSecClassGenericPassword)
            query.setValue(kSecAttrService, serviceString)
            query.setValue(kSecAttrAccount, aliasString)
            query.setValue(kSecAttrSynchronizable, kCFBooleanFalse)
            query.setValue(kSecUseDataProtectionKeychain, kCFBooleanTrue)
            return block(query)
        } finally {
            query.release()
            serviceString.release()
            aliasString.release()
        }
    }

    private fun securityConstant(name: String): CFStringRef =
        CFStringRef(
            securitySymbols.getGlobalVariableAddress(name).getPointer(0)
                ?: throw StorageProtectionException("Security.framework symbol '$name' is unavailable"),
        )

    private fun coreBooleanConstant(name: String): CFBooleanRef =
        CFBooleanRef(
            coreSymbols.getGlobalVariableAddress(name).getPointer(0)
                ?: throw StorageProtectionException("CoreFoundation symbol '$name' is unavailable"),
        )
}

private interface MacSecurityLibrary : Library {
    fun SecItemCopyMatching(query: CFDictionaryRef, result: PointerByReference): Int

    fun SecItemAdd(attributes: CFDictionaryRef, result: PointerByReference?): Int

    fun SecRandomCopyBytes(random: Pointer?, count: NativeLong, bytes: Pointer): Int
}
