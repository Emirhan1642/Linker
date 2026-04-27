# Design Document Fixes - Applied

## ✅ Critical Fixes Applied

### 1. BLE Packet Serialization Bug
- **Fixed**: HEADER_SIZE corrected from 101 to 121 bytes
- **Location**: Line ~807 in design.md
- **Impact**: Property 9 (serialization round-trip) will now pass

### 2. Signal Protocol API Correction
- **Fixed**: EncryptedMessage now uses `signalMessage: ByteArray` instead of separate fields
- **Location**: Line ~600 in design.md
- **Impact**: Correct Signal Protocol integration

### 3. Backoff Delay Consistency
- **Fixed**: BLEErrorHandler now uses `RetryStrategy.INITIAL_DELAY` (5000ms)
- **Location**: Error Handling section
- **Impact**: Consistent with Requirement 14

### 4. Fragment Reassembly Memory Management
- **Added**: FragmentManager class with 30-second timeout
- **Location**: After PacketFragmenter in Data Models section
- **Impact**: Prevents memory leaks from incomplete fragments

### 5. Race Condition Handling (Req 18.8)
- **Added**: MessageDeduplicationManager class
- **Location**: After QueueStatus in Components section
- **Impact**: Handles messages arriving via both BLE and online

## ✅ Important Fixes Applied

### 6. Priority Semantics Clarification
- **Added**: Documentation explaining priority 0 = higher priority
- **Location**: MessageQueueEntity section
- **Impact**: Clear semantics for developers

### 7. Key Exchange UX Flow
- **Added**: Detailed UX flow for missing encryption keys
- **Location**: Known Limitations section
- **Impact**: Better user experience for key exchange failures

## 🔄 Remaining Minor Fixes (To Be Applied in Implementation)

### 8. Service Status Tracking
- **Note**: Use StateFlow instead of deprecated `getRunningServices()`
- **Implementation**: In OfflineMessagingServiceManager

### 9. Doze Mode Strategy
- **Note**: Use WorkManager for sync, Foreground Service for BLE
- **Implementation**: In OfflineMessagingService

### 10. Kotest Syntax
- **Note**: Use `assume()` or `filter()` instead of `whenever()`
- **Implementation**: In property tests

### 11. Hilt Broadcast Receiver
- **Note**: Use EntryPointAccessors pattern
- **Implementation**: In BootCompletedReceiver

## Summary

**Critical Fixes**: 5/5 ✅
**Important Fixes**: 2/2 ✅
**Minor Fixes**: 4/4 (documented for implementation)

All critical and important issues identified by Claude Sonnet 4.6 have been addressed in the design document. Minor fixes are documented and will be applied during implementation phase.

## Next Steps

1. Review updated design.md
2. Proceed to tasks.md creation
3. Apply minor fixes during implementation
