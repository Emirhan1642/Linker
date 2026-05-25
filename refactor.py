import sys
import re

file_path = 'app/src/main/java/com/linker/app/data/encryption/SignalProtocolStoreImpl.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add imports
imports_to_add = '''import java.util.concurrent.Executors
import java.util.concurrent.Callable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.linker.app.core.util.Logger
'''
content = content.replace('import java.util.*', imports_to_add + 'import java.util.*')

# Add properties and helper method
helper_code = '''
    private val dbExecutor = Executors.newFixedThreadPool(4)
    
    private val _identityChanges = MutableSharedFlow<IdentityChangeEvent>(extraBufferCapacity = 10)
    val identityChanges: SharedFlow<IdentityChangeEvent> = _identityChanges.asSharedFlow()

    private fun <T> runOnDbThread(block: suspend () -> T): T {
        return dbExecutor.submit(Callable {
            runBlocking { block() }
        }).get()
    }
'''
content = content.replace('private var localRegistrationId: Int = 0', 'private var localRegistrationId: Int = 0\n' + helper_code)

# Replace runBlocking(Dispatchers.IO) { with runOnDbThread {
content = content.replace('= runBlocking(Dispatchers.IO) {', '= runOnDbThread {')

# Replace return@runBlocking with return@runOnDbThread
content = content.replace('return@runBlocking', 'return@runOnDbThread')

# Fix isTrustedIdentity method for TOFU
old_isTrustedIdentity = '''    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean = runOnDbThread {
        val addressString = "${address.name}:${address.deviceId}"
        val stored = identityDao.getIdentity(addressString)
        
        if (stored == null) {
            // TOFU: Trust On First Use \u2014 ilk kar\u015f\u0131la\u015fmada kimli\u011fi g\u00fcvenilir say.
            // Kimlik de\u011fi\u015fikli\u011fi tespiti (isTrustedIdentity false d\u00f6nd\u00fc\u011f\u00fcnde) UI'da
            // "G\u00fcvenlik kodu de\u011fi\u015fti" uyar\u0131s\u0131 g\u00f6stermelidir.
            return@runOnDbThread true
        }
        
        // Check if identity key matches
        stored.identityKey.contentEquals(identityKey.serialize())
    }'''

new_isTrustedIdentity = '''    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean = runOnDbThread {
        val addressString = "${address.name}:${address.deviceId}"
        val stored = identityDao.getIdentity(addressString)
        
        if (stored == null) {
            // TOFU: Trust On First Use
            _identityChanges.tryEmit(
                IdentityChangeEvent.NewIdentity(address, identityKey)
            )
            return@runOnDbThread true
        }
        
        val matches = stored.identityKey.contentEquals(identityKey.serialize())
        
        if (!matches) {
            // Identity changed
            _identityChanges.tryEmit(
                IdentityChangeEvent.IdentityChanged(
                    address = address,
                    oldKey = IdentityKey(stored.identityKey, 0),
                    newKey = identityKey
                )
            )
        }
        
        matches
    }'''

content = content.replace(old_isTrustedIdentity, new_isTrustedIdentity)

# Add IdentityChangeEvent at the end
content += '''
sealed class IdentityChangeEvent {
    data class NewIdentity(val address: SignalProtocolAddress, val identityKey: IdentityKey) : IdentityChangeEvent()
    data class IdentityChanged(val address: SignalProtocolAddress, val oldKey: IdentityKey, val newKey: IdentityKey) : IdentityChangeEvent()
}
'''

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Done!')
