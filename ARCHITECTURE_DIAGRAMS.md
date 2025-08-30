# DeepSeekChat4 Architecture Diagrams

## 1. Overall Application Architecture

```mermaid
graph TB
    subgraph "Presentation Layer"
        A[StartActivity] --> B[MainActivity]
        B --> C[AudioAiActivity]
        B --> D[ImageGenerationActivity]
        B --> E[SettingsActivity]
        
        F[HomeFragment]
        G[HistoryFragment]
        H[SavedConversationsFragment]
        
        I[ChatAdapter]
        J[ConversationAdapter]
        K[ModelSpinnerAdapter]
    end
    
    subgraph "Domain Layer"
        L[SendMessageUseCase]
        M[CreateConversationUseCase]
        N[GetConversationsUseCase]
        O[StreamingUseCase]
    end
    
    subgraph "Data Layer"
        P[ChatRepository]
        Q[AppDatabase]
        R[RetrofitInstance]
        S[StreamingHandler]
        
        Q --> T[ChatMessageDao]
        Q --> U[ConversationDao]
        Q --> V[CoreMessageDao]
        Q --> W[MessageMetadataDao]
    end
    
    subgraph "Core Services"
        X[BillingManager]
        Y[SecurityManager]
        Z[AdManager]
        AA[UnifiedStreamingManager]
        BB[ThemeManager]
    end
    
    subgraph "External APIs"
        CC[AIML API]
        DD[Together AI]
        EE[Google APIs]
        FF[Huawei HMS]
    end
    
    B --> F
    B --> G
    B --> H
    
    I --> L
    J --> M
    
    L --> P
    M --> P
    N --> P
    O --> P
    
    P --> Q
    P --> R
    R --> S
    
    X --> FF
    X --> EE
    Y --> BB
    
    R --> CC
    R --> DD
    R --> EE
    
    MyApp[MyApp] --> X
    MyApp --> Y
    MyApp --> Z
    MyApp --> AA
    MyApp --> BB
```

## 2. Security Architecture

```mermaid
graph TB
    subgraph "Security Layer"
        A[SecurityManager] --> B[BiometricAuthManager]
        A --> C[EncryptedStorageManager]
        A --> D[PrivacyManager]
        A --> E[SecureCommunicationManager]
        A --> F[SecuritySettingsManager]
    end
    
    subgraph "Authentication"
        B --> G[Biometric Prompt]
        B --> H[PIN/Password]
        B --> I[Device Lock Integration]
    end
    
    subgraph "Data Protection"
        C --> J[Android Keystore]
        C --> K[AES-GCM Encryption]
        C --> L[Encrypted SharedPreferences]
        
        D --> M[Data Minimization]
        D --> N[Privacy Controls]
        D --> O[Automatic Cleanup]
    end
    
    subgraph "Network Security"
        E --> P[Certificate Pinning]
        E --> Q[Network Security Config]
        E --> R[TLS Verification]
    end
    
    subgraph "Threat Detection"
        A --> S[Runtime Monitoring]
        A --> T[Anomaly Detection]
        A --> U[Automated Scans]
    end
    
    subgraph "External Integration"
        J --> V[System Keystore]
        P --> W[Network Layer]
        S --> X[Performance Monitor]
    end
```

## 3. Billing & Multi-Platform Architecture

```mermaid
graph TB
    subgraph "Device Detection"
        A[MyApp.isHuaweiDevice] --> B{Brand Check}
        A --> C{HMS Core Check}
        B --> D[Device Cache]
        C --> D
    end
    
    subgraph "Billing Manager"
        D --> E[BillingManager]
        E --> F{Platform Decision}
        
        F -->|Google Device| G[GooglePlayBillingProvider]
        F -->|Huawei Device| H[HuaweiIapProvider]
    end
    
    subgraph "Google Play Integration"
        G --> I[Play Billing Library]
        I --> J[Google Play Store]
        I --> K[Play Services]
    end
    
    subgraph "Huawei Integration"
        H --> L[HMS IAP SDK]
        L --> M[Huawei AppGallery]
        L --> N[HMS Core]
    end
    
    subgraph "Unified Interface"
        E --> O[BillingProvider Interface]
        O --> P[Product Queries]
        O --> Q[Purchase Flow]
        O --> R[Subscription Management]
    end
    
    subgraph "App Integration"
        P --> S[Premium Features]
        Q --> T[Credit System]
        R --> U[Feature Unlocking]
    end
```

## 4. AI Chat Flow Architecture

```mermaid
sequenceDiagram
    participant U as User
    participant MA as MainActivity
    participant CA as ChatAdapter
    participant SM as SendMessageUseCase
    parameter CR as ChatRepository
    participant SH as StreamingHandler
    participant API as AI API
    participant DB as AppDatabase
    
    U->>MA: Type message
    MA->>CA: Add user message
    CA->>DB: Store user message
    
    MA->>SM: Send message request
    SM->>CR: Process message
    CR->>SH: Start streaming
    
    SH->>API: HTTP POST (streaming)
    API-->>SH: Stream chunks
    
    loop Streaming Response
        SH->>CA: Update partial content
        CA->>MA: Display streaming text
        MA->>U: Show real-time response
    end
    
    SH->>DB: Store complete message
    SH->>CA: Mark streaming complete
    CA->>MA: Enable user input
```

## 5. Data Layer Architecture

```mermaid
graph TB
    subgraph "Database Schema"
        A[AppDatabase v5] --> B[Legacy Tables]
        A --> C[Optimized Tables]
        
        B --> D[ChatMessage]
        B --> E[Conversation]
        B --> F[Branch]
        
        C --> G[CoreMessage]
        C --> H[MessageMetadata]
        C --> I[MessageState]
        C --> J[MessageContentData]
        C --> K[MessagePerformance]
    end
    
    subgraph "Data Access Objects"
        L[ChatMessageDao] --> D
        M[ConversationDao] --> E
        N[CoreMessageDao] --> G
        O[MessageMetadataDao] --> H
        P[MessageStateDao] --> I
        Q[MessageContentDataDao] --> J
        R[MessagePerformanceDao] --> K
    end
    
    subgraph "Repository Layer"
        S[ChatRepositoryImpl] --> L
        S --> M
        S --> N
        S --> O
        S --> P
        S --> Q
        S --> R
    end
    
    subgraph "Migration Strategy"
        T[Migration 4→5] --> U[Create New Tables]
        T --> V[Preserve Legacy Data]
        T --> W[Performance Indices]
    end
    
    subgraph "Performance Optimization"
        X[Indexed Queries] --> G
        X --> H
        Y[Foreign Key Constraints] --> G
        Z[Normalized Schema] --> AA[Reduced Memory Usage]
    end
```

## 6. Dependency & Build Architecture

```mermaid
graph TB
    subgraph "Build System"
        A[Root build.gradle.kts] --> B[App build.gradle.kts]
        A --> C[gradle/libs.versions.toml]
        
        B --> D[Android Config]
        B --> E[Dependencies]
        B --> F[Build Types]
        
        D --> G[compileSdk: 35]
        D --> H[minSdk: 28]
        D --> I[targetSdk: 35]
        
        F --> J[Debug Build]
        F --> K[Release Build]
        K --> L[ProGuard/R8]
        K --> M[Signing Config]
    end
    
    subgraph "Dependency Categories"
        E --> N[Core Android - 15]
        E --> O[UI Frameworks - 12]
        E --> P[Networking - 8]
        E --> Q[Database - 3]
        E --> R[Security - 4]
        E --> S[Document Processing - 12]
        E --> T[Multi-platform - 8]
        E --> U[Testing - 3]
    end
    
    subgraph "Complex Dependencies"
        S --> V[Apache POI]
        S --> W[PDFBox Android]
        S --> X[Commons Libraries]
        V --> Y[Many Exclusions]
        W --> Z[Platform Compatibility]
    end
    
    subgraph "Multi-Platform Support"
        T --> AA[Google Play Services]
        T --> BB[Huawei HMS]
        T --> CC[Ad Networks]
        AA --> DD[Play Billing]
        BB --> EE[Huawei IAP]
    end
```

These diagrams provide a comprehensive visual overview of the DeepSeekChat4 application architecture, showing the relationships between different layers, components, and external dependencies.