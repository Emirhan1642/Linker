# Linker - Architecture Documentation

## 📐 Architecture Overview

Linker follows **Clean Architecture** principles with clear separation of concerns across three main layers:

```
┌─────────────────────────────────────────────────────────────┐
│                     Presentation Layer                       │
│  (UI, ViewModels, Compose Screens, Navigation)             │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                      Domain Layer                            │
│  (Use Cases, Domain Models, Repository Interfaces)          │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                       Data Layer                             │
│  (Repositories, Data Sources, Room, Retrofit, Firebase)     │
└─────────────────────────────────────────────────────────────┘
```

## 🏗️ Layer Details

### 1. Presentation Layer

**Responsibility**: UI rendering and user interaction

**Components**:
- **Screens**: Composable functions for each screen
- **ViewModels**: State management and business logic coordination
- **Navigation**: Jetpack Navigation Compose
- **Theme**: Material 3 theming system

**Pattern**: MVVM (Model-View-ViewModel)

**Example**:
```kotlin
@Composable
fun ChatMessageScreen(
    viewModel: ChatMessageViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // UI rendering based on state
    LazyColumn {
        items(uiState.messages) { message ->
            MessageBubble(message)
        }
    }
}
```

### 2. Domain Layer

**Responsibility**: Business logic and rules

**Components**:
- **Models**: Pure Kotlin data classes (no Android dependencies)
- **Use Cases**: Single-responsibility business operations
- **Repository Interfaces**: Contracts for data operations

**Benefits**:
- Platform-independent
- Testable without Android framework
- Reusable across different platforms

**Example**:
```kotlin
class SendMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(
        chatId: String,
        content: String
    ): Result<Message> {
        return messageRepository.sendMessage(
            chatId = chatId,
            messageType = MessageType.TEXT,
            content = content
        )
    }
}
```

### 3. Data Layer

**Responsibility**: Data management and persistence

**Components**:
- **Repositories**: Implementation of domain interfaces
- **Data Sources**: Local (Room) and Remote (Firebase, Supabase)
- **Mappers**: Convert between data and domain models

**Data Flow**:
```
Remote API → Repository → Domain Model → ViewModel → UI
     ↓
Local Cache (Room)
```

**Example**:
```kotlin
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val chatDao: ChatDao
) : ChatRepository {
    override suspend fun sendMessage(...): Result<Message> {
        // 1. Save to local database
        // 2. Upload to Firestore
        // 3. Return result
    }
}
```

## 🔄 Data Flow

### Typical Flow: Sending a Message

```
1. User types message in ChatMessageScreen
2. Screen calls viewModel.sendMessage()
3. ViewModel calls SendMessageUseCase
4. UseCase calls ChatRepository.sendMessage()
5. Repository:
   a. Saves to Room (local cache)
   b. Uploads to Firestore
   c. Queues for offline sync if needed
6. Repository returns Result<Message>
7. ViewModel updates UI state
8. Screen recomposes with new message
```

### Offline-First Strategy

```
┌──────────────┐
│  User Action │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  Local DB    │ ← Always write here first
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  Sync Queue  │ ← Queue for background sync
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  Remote API  │ ← Sync when online
└──────────────┘
```

## 🎯 Design Patterns

### 1. Repository Pattern
- Abstracts data sources
- Single source of truth
- Handles caching strategy

### 2. Use Case Pattern
- Single responsibility
- Reusable business logic
- Easy to test

### 3. Observer Pattern
- StateFlow for reactive UI
- Flow for data streams
- LiveData for lifecycle-aware updates

### 4. Dependency Injection
- Hilt for compile-time DI
- Constructor injection preferred
- Field injection for Android components

### 5. Result Wrapper
```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: String) : Result<Nothing>()
    data class Loading(val progress: Float? = null) : Result<Nothing>()
}
```

## 📦 Module Structure

### Core Modules
- **app**: Main application module
- **core**: Shared utilities and base classes
- **data**: Data layer implementation
- **domain**: Business logic and models
- **presentation**: UI components

### Feature Modules (Future)
- **feature-chat**: Chat functionality
- **feature-story**: Stories feature
- **feature-link**: Posts/Links feature
- **feature-profile**: User profiles

## 🔐 Security Architecture

### 1. Credential Storage
```
BuildConfig (compile-time)
    ↓
SecurityManager (runtime)
    ↓
EncryptedSharedPreferences (Android Keystore)
```

### 2. API Key Management
- Keys stored in `local.properties` (not in VCS)
- Encrypted at runtime using Android Keystore
- Never exposed in logs or crash reports

### 3. Authentication Flow
```
Firebase Auth → Custom Token → Encrypted Storage → Auto-refresh
```

## 🧪 Testing Strategy

### Unit Tests
- Domain layer (Use Cases, Models)
- ViewModels (with test coroutines)
- Utilities and helpers

### Integration Tests
- Repository implementations
- Database operations
- API calls

### UI Tests
- Compose UI tests
- Navigation flows
- User interactions

## 📊 State Management

### ViewModel State Pattern
```kotlin
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSending: Boolean = false
)

class ChatViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    fun sendMessage(content: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            // Send message logic
            _uiState.update { it.copy(isSending = false) }
        }
    }
}
```

## 🚀 Performance Optimizations

### 1. Database
- Indexed columns for fast queries
- Pagination for large lists
- Lazy loading for media

### 2. Network
- Request caching
- Batch operations
- Retry with exponential backoff

### 3. UI
- LazyColumn for lists
- remember for expensive calculations
- derivedStateOf for computed values

### 4. Memory
- Coil for image loading (automatic caching)
- Paging 3 for infinite lists
- ViewModel scoping for lifecycle

## 📝 Code Conventions

### Naming
- **Classes**: PascalCase
- **Functions**: camelCase
- **Constants**: UPPER_SNAKE_CASE
- **Packages**: lowercase

### File Organization
- One class per file
- Group related files in packages
- Keep files under 500 lines

### Documentation
- KDoc for public APIs
- Inline comments for complex logic
- README for each module

## 🔄 Migration Strategy

### Database Migrations
- Room auto-migration when possible
- Manual migration for complex changes
- Test migrations before release

### API Versioning
- Version endpoints (/v1/, /v2/)
- Backward compatibility for 2 versions
- Deprecation warnings

## 🎨 UI Architecture

### Compose Best Practices
- Stateless composables
- State hoisting
- Side effects in LaunchedEffect
- Remember expensive operations

### Theme System
- Material 3 dynamic colors
- Dark/Light mode support
- Custom color schemes
- Typography scale

## 📱 Offline Architecture

### Sync Strategy
1. **Write-through**: Save locally, sync immediately
2. **Write-behind**: Save locally, sync in background
3. **Conflict Resolution**: Last-write-wins with timestamps

### Queue System
- WorkManager for reliable background sync
- Retry with exponential backoff
- Network-aware scheduling

## 🔮 Future Enhancements

1. **Modularization**: Split into feature modules
2. **KMP**: Share code with iOS
3. **Compose Multiplatform**: Desktop/Web support
4. **GraphQL**: Replace REST APIs
5. **WebSocket**: Real-time updates

---

**Last Updated**: April 2026  
**Version**: 1.0  
**Maintainer**: Emirhan
