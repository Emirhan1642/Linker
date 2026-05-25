package com.linker.app.data.bluetooth

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.linker.app.BuildConfig
import com.linker.app.core.util.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detailed Bluetooth adapter states exposed to observers.
 */
enum class BluetoothState {
    OFF,
    TURNING_ON,
    ON,
    TURNING_OFF,
    ERROR
}

/**
 * Errors surfaced from [BluetoothManager] operations.
 */
sealed class BluetoothError {
    data class PermissionDenied(val permission: String) : BluetoothError()
    data class AdapterNotAvailable(val reason: String) : BluetoothError()
    data class OperationFailed(val operation: String, val throwable: Throwable?) : BluetoothError()
}

/**
 * Manager for Bluetooth state and operations.
 *
 * Handles checking enablement, enabling/disabling Bluetooth, and observing state changes.
 */
interface BluetoothManager {

    fun isBluetoothEnabled(): Boolean

    fun hasBluetoothConnectPermission(): Boolean

    /**
     * @return true if an enable request was sent (actual enable is asynchronous)
     */
    fun enableBluetooth(): Boolean

    fun disableBluetooth(): Boolean

    fun getBluetoothAdapter(): BluetoothAdapter?

    fun observeBluetoothState(): StateFlow<Boolean>

    fun observeBluetoothDetailedState(): StateFlow<BluetoothState>

    val errors: SharedFlow<BluetoothError>

    fun startListening()

    fun stopListening()

    /**
     * Initialize the manager (call from [android.app.Application.onCreate]).
     */
    fun initialize()

    /**
     * Release receiver resources when the process is shutting down.
     */
    fun cleanup()

    fun openBluetoothSettings(context: Context)

    /**
     * Enable Bluetooth and wait until [observeBluetoothState] reports enabled or timeout.
     */
    suspend fun enableBluetoothWithTimeout(timeoutMs: Long = BluetoothManagerImpl.ENABLE_TIMEOUT_MS): Result<Boolean>

    /**
     * Enable Bluetooth with structured [Result] handling.
     *
     * @return [Result.success] with `true` when a new enable request was sent,
     * `false` when Bluetooth was already enabled, or [Result.failure] on error.
     */
    suspend fun enableBluetoothAsync(): Result<Boolean>

    fun getDiagnosticInfo(): Map<String, Any>
}

/**
 * Implementation of [BluetoothManager].
 *
 * Thread-safe singleton that registers a [BroadcastReceiver] for adapter state changes
 * and exposes reactive updates via [StateFlow]. Use reference-counted
 * [startListening]/[stopListening] from screens, and [initialize]/[cleanup] at app level.
 */
@Singleton
class BluetoothManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BluetoothManager {

    private val _bluetoothAdapter: BluetoothAdapter? by lazy { resolveBluetoothAdapter() }

    private val _bluetoothState = MutableStateFlow(false)
    private val _bluetoothDetailedState = MutableStateFlow(BluetoothState.OFF)

    private val _errors = MutableSharedFlow<BluetoothError>(
        extraBufferCapacity = 16
    )
    override val errors: SharedFlow<BluetoothError> = _errors.asSharedFlow()

    private val isInitialized = AtomicBoolean(false)
    private val receiverRegistered = AtomicBoolean(false)
    private val listenerRefCount = AtomicInteger(0)
    private val receiverLock = Any()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            try {
                if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return

                val adapterState = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )

                if (adapterState == BluetoothAdapter.ERROR) {
                    logError("Received ERROR state from Bluetooth adapter")
                    _bluetoothDetailedState.value = BluetoothState.ERROR
                    return
                }

                val detailedState = when (adapterState) {
                    BluetoothAdapter.STATE_OFF -> BluetoothState.OFF
                    BluetoothAdapter.STATE_TURNING_ON -> BluetoothState.TURNING_ON
                    BluetoothAdapter.STATE_ON -> BluetoothState.ON
                    BluetoothAdapter.STATE_TURNING_OFF -> BluetoothState.TURNING_OFF
                    else -> {
                        logWarning("Unknown Bluetooth state: $adapterState")
                        BluetoothState.ERROR
                    }
                }

                _bluetoothDetailedState.value = detailedState

                when (detailedState) {
                    BluetoothState.ON -> {
                        _bluetoothState.value = true
                        logDebug("Bluetooth state updated: ON")
                    }
                    BluetoothState.OFF -> {
                        _bluetoothState.value = false
                        logDebug("Bluetooth state updated: OFF")
                    }
                    BluetoothState.TURNING_ON -> logDebug("Bluetooth is turning on")
                    BluetoothState.TURNING_OFF -> logDebug("Bluetooth is turning off")
                    BluetoothState.ERROR -> Unit
                }
            } catch (e: Exception) {
                logError("Error in Bluetooth receiver", e)
            }
        }
    }

    init {
        syncBluetoothStateFromAdapter()
    }

    override fun initialize() {
        if (isInitialized.compareAndSet(false, true)) {
            syncBluetoothStateFromAdapter()
            startListening()
            logDebug("BluetoothManager initialized")
        }
    }

    override fun cleanup() {
        if (isInitialized.compareAndSet(true, false)) {
            listenerRefCount.set(0)
            unregisterReceiverLocked()
            logDebug("BluetoothManager cleaned up")
        }
    }

    override fun isBluetoothEnabled(): Boolean {
        return _bluetoothAdapter?.isEnabled ?: false
    }

    override fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun enableBluetooth(): Boolean {
        return try {
            if (!hasBluetoothConnectPermission()) {
                logError("BLUETOOTH_CONNECT permission not granted")
                _errors.tryEmit(BluetoothError.PermissionDenied(Manifest.permission.BLUETOOTH_CONNECT))
                return false
            }

            val adapter = _bluetoothAdapter
            if (adapter == null) {
                logError("Bluetooth adapter is null")
                _errors.tryEmit(BluetoothError.AdapterNotAvailable("Adapter is null"))
                return false
            }

            if (adapter.isEnabled) {
                logDebug("Bluetooth is already enabled")
                return true
            }

            logDebug("Attempting to enable Bluetooth")
            val result = adapter.enable()
            if (!result) {
                logWarning("Bluetooth enable() returned false; async enable may still proceed")
            }
            true
        } catch (e: SecurityException) {
            logError("SecurityException in enableBluetooth", e)
            _errors.tryEmit(BluetoothError.OperationFailed("enableBluetooth", e))
            false
        } catch (e: Exception) {
            logError("Exception in enableBluetooth", e)
            _errors.tryEmit(BluetoothError.OperationFailed("enableBluetooth", e))
            false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun disableBluetooth(): Boolean {
        return try {
            if (!hasBluetoothConnectPermission()) {
                logError("BLUETOOTH_CONNECT permission not granted")
                _errors.tryEmit(BluetoothError.PermissionDenied(Manifest.permission.BLUETOOTH_CONNECT))
                return false
            }

            val adapter = _bluetoothAdapter
            if (adapter == null) {
                logError("Bluetooth adapter is null")
                _errors.tryEmit(BluetoothError.AdapterNotAvailable("Adapter is null"))
                return false
            }

            if (!adapter.isEnabled) {
                logDebug("Bluetooth is already disabled")
                return true
            }

            logDebug("Attempting to disable Bluetooth")
            adapter.disable()
        } catch (e: SecurityException) {
            logError("SecurityException in disableBluetooth", e)
            _errors.tryEmit(BluetoothError.OperationFailed("disableBluetooth", e))
            false
        } catch (e: Exception) {
            logError("Exception in disableBluetooth", e)
            _errors.tryEmit(BluetoothError.OperationFailed("disableBluetooth", e))
            false
        }
    }

    override suspend fun enableBluetoothAsync(): Result<Boolean> {
        return try {
            if (!hasBluetoothConnectPermission()) {
                _errors.tryEmit(BluetoothError.PermissionDenied(Manifest.permission.BLUETOOTH_CONNECT))
                return Result.failure(SecurityException("BLUETOOTH_CONNECT not granted"))
            }

            val adapter = _bluetoothAdapter
            if (adapter == null) {
                _errors.tryEmit(BluetoothError.AdapterNotAvailable("Adapter is null"))
                return Result.failure(IllegalStateException("Bluetooth adapter unavailable"))
            }

            if (adapter.isEnabled) {
                return Result.success(false)
            }

            val sent = adapter.enable()
            if (!sent) {
                logWarning("Bluetooth enable() returned false; async enable may still proceed")
            }
            Result.success(true)
        } catch (e: SecurityException) {
            _errors.tryEmit(BluetoothError.OperationFailed("enableBluetoothAsync", e))
            Result.failure(e)
        } catch (e: Exception) {
            _errors.tryEmit(BluetoothError.OperationFailed("enableBluetoothAsync", e))
            Result.failure(e)
        }
    }

    override suspend fun enableBluetoothWithTimeout(timeoutMs: Long): Result<Boolean> {
        if (!enableBluetooth()) {
            return Result.failure(IllegalStateException("Bluetooth enable request failed"))
        }

        if (isBluetoothEnabled()) {
            return Result.success(true)
        }

        val enabled = withTimeoutOrNull(timeoutMs) {
            observeBluetoothState().filter { it }.first()
        }

        return when (enabled) {
            true -> Result.success(true)
            null -> Result.failure(
                TimeoutException("Bluetooth enable timed out after ${timeoutMs}ms")
            )
            else -> Result.failure(IllegalStateException("Bluetooth did not enable"))
        }
    }

    override fun getBluetoothAdapter(): BluetoothAdapter? = _bluetoothAdapter

    override fun observeBluetoothState(): StateFlow<Boolean> {
        syncBluetoothStateFromAdapter()
        return _bluetoothState.asStateFlow()
    }

    override fun observeBluetoothDetailedState(): StateFlow<BluetoothState> {
        syncBluetoothStateFromAdapter()
        return _bluetoothDetailedState.asStateFlow()
    }

    override fun startListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnectPermission()) {
            logError("Cannot start listening: BLUETOOTH_CONNECT permission not granted")
            return
        }

        val count = listenerRefCount.incrementAndGet()
        if (count == 1) {
            if (!registerReceiverLocked()) {
                listenerRefCount.decrementAndGet()
            }
        } else {
            logDebug("Bluetooth listener ref count: $count")
        }
    }

    override fun stopListening() {
        val count = listenerRefCount.decrementAndGet()
        if (count <= 0) {
            listenerRefCount.set(0)
            unregisterReceiverLocked()
        } else {
            logDebug("Bluetooth listener ref count: $count")
        }
    }

    override fun openBluetoothSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                logDebug("Bluetooth settings opened")
            } else {
                logError("No activity found to handle Bluetooth settings intent")
            }
        } catch (e: ActivityNotFoundException) {
            logError("Bluetooth settings activity not found", e)
        } catch (e: SecurityException) {
            logError("Security exception opening Bluetooth settings", e)
        } catch (e: Exception) {
            logError("Failed to open Bluetooth settings", e)
        }
    }

    override fun getDiagnosticInfo(): Map<String, Any> {
        return mapOf(
            "adapterAvailable" to (_bluetoothAdapter != null),
            "bluetoothEnabled" to isBluetoothEnabled(),
            "receiverRegistered" to receiverRegistered.get(),
            "listenerRefCount" to listenerRefCount.get(),
            "isInitialized" to isInitialized.get(),
            "hasPermission" to hasBluetoothConnectPermission(),
            "currentState" to _bluetoothState.value,
            "detailedState" to _bluetoothDetailedState.value.name
        )
    }

    private fun resolveBluetoothAdapter(): BluetoothAdapter? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val systemBluetoothManager =
                    context.getSystemService(Context.BLUETOOTH_SERVICE)
                        as? android.bluetooth.BluetoothManager
                systemBluetoothManager?.adapter
            } else {
                @Suppress("DEPRECATION")
                BluetoothAdapter.getDefaultAdapter()
            }
        } catch (e: Exception) {
            logError("Error getting Bluetooth adapter", e)
            null
        }
    }

    private fun syncBluetoothStateFromAdapter() {
        val enabled = _bluetoothAdapter?.isEnabled ?: false
        if (_bluetoothState.value != enabled) {
            _bluetoothState.value = enabled
            logDebug("Bluetooth state synchronized: $enabled")
        }
        when (_bluetoothDetailedState.value) {
            BluetoothState.TURNING_ON, BluetoothState.TURNING_OFF -> return
            else -> {
                val target = if (enabled) BluetoothState.ON else BluetoothState.OFF
                if (_bluetoothDetailedState.value != target) {
                    _bluetoothDetailedState.value = target
                }
            }
        }
    }

    private fun registerReceiverLocked(): Boolean {
        synchronized(receiverLock) {
            if (receiverRegistered.get()) return true

            return try {
                val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(
                        bluetoothReceiver,
                        filter,
                        Context.RECEIVER_EXPORTED
                    )
                } else {
                    context.registerReceiver(bluetoothReceiver, filter)
                }
                receiverRegistered.set(true)
                logDebug("Bluetooth receiver registered")
                true
            } catch (e: IllegalArgumentException) {
                logError("Receiver already registered", e)
                receiverRegistered.set(true)
                true
            } catch (e: Exception) {
                logError("Error registering Bluetooth receiver", e)
                false
            }
        }
    }

    private fun unregisterReceiverLocked() {
        synchronized(receiverLock) {
            if (!receiverRegistered.get()) return

            try {
                context.unregisterReceiver(bluetoothReceiver)
                logDebug("Bluetooth receiver unregistered")
            } catch (e: IllegalArgumentException) {
                logWarning("Bluetooth receiver was not registered")
            } catch (e: Exception) {
                logError("Error unregistering Bluetooth receiver", e)
            } finally {
                receiverRegistered.set(false)
            }
        }
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            SecureLogger.d(TAG, message)
        }
    }

    private fun logWarning(message: String, throwable: Throwable? = null) {
        SecureLogger.w(TAG, message, throwable)
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        SecureLogger.e(TAG, message, throwable)
    }

    companion object {
        private const val TAG = "BluetoothManager"
        const val ENABLE_TIMEOUT_MS = 10_000L
        const val DISABLE_TIMEOUT_MS = 5_000L
    }
}
