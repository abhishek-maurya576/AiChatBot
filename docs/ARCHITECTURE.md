# AI Chat Bot Agent - Architecture Overview

## System Architecture

The AI Chat Bot Agent follows a modern Android architecture with a focus on separation of concerns, testability, and maintainability. The application is built using the MVVM (Model-View-ViewModel) pattern with a clean architecture approach.

### High-Level Components

```
┌────────────────┐     ┌────────────────┐     ┌────────────────┐
│                │     │                │     │                │
│  Presentation  │◄────┤   Domain       │◄────┤    Data        │
│  Layer         │     │   Layer        │     │    Layer       │
│                │     │                │     │                │
└────────────────┘     └────────────────┘     └────────────────┘
```

## Layer Details

### Presentation Layer

The presentation layer is responsible for displaying data to the user and handling user interactions. It consists of:

- **Activities**: `MainActivity` - Main entry point for the application
- **Compose UI**: Screen composables for chat, agent controls, and settings
- **ViewModels**: Manage UI-related data and business logic
  - `ChatViewModel`: Handles chat interactions and AI responses
  - `AgentViewModel`: Manages agent configuration and operation

Key classes:
- `MainActivity.kt` - Main activity handling navigation and permissions
- `ChatScreen.kt` - UI for interacting with the AI chatbot
- `AiAgentScreen.kt` - UI for configuring and controlling the AI agent

### Domain Layer

The domain layer contains the business logic of the application and is independent of other layers:

- **Use Cases**: Encapsulate business logic operations
- **Domain Models**: Core business entities
- **Repositories Interfaces**: Define data operations contract

Key components:
- `CommandProcessor.kt` - Processes user commands and determines appropriate actions
- `ChatMessage.kt` - Domain model for messages

### Data Layer

The data layer is responsible for retrieving and storing data:

- **Repositories Implementation**: Implement interfaces from domain layer
- **Remote Data Source**: APIs for external services
- **Local Data Source**: Database access for local storage
- **Data Mappers**: Convert between data and domain models

Key classes:
- `GeminiService.kt` - Integration with Google's Gemini AI API
- `ChatRepository.kt` - Manages chat data operations
- `Room Database` - Local storage for chat history

## Key Components

### Accessibility Service

The `AccessibilityService` is a core component that enables the AI Agent to interact with other applications:

- **AppControlManager.kt**: Handles interactions with other apps through accessibility events
- **Node Navigation**: Traverses UI hierarchies to find appropriate elements
- **UI Automation**: Performs clicks, typing, and other interactions

### Floating Eye Widget

The `FloatingEyeService` provides visual feedback of AI operations:

- **Window Manager Integration**: Displays overlay UI on screen
- **Drag Detection**: Enables moving the widget around
- **Visual Feedback**: Shows operation status and details

### Command Processing

The command processing pipeline involves:

1. User input (text/voice)
2. AI analysis via Gemini API
3. Intent extraction and parameter identification
4. Action mapping and execution
5. Feedback generation

## Data Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│             │     │             │     │             │     │             │
│  User Input ├────►│ AI Analysis ├────►│  Command    ├────►│  Action     │
│             │     │             │     │  Processing │     │  Execution  │
│             │     │             │     │             │     │             │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
                                                                   │
┌─────────────┐                                                    │
│             │                                                    │
│   Result    │◄───────────────────────────────────────────────────┘
│   Feedback  │
│             │
└─────────────┘
```

## Threading Model

- **Main Thread**: UI rendering and user interaction
- **Background Operations**: Coroutines for asynchronous operations
- **Service Thread**: Accessibility and floating eye services

## Dependencies

- **UI**: Jetpack Compose, Material Design
- **Async**: Kotlin Coroutines, Flow
- **DI**: Hilt for dependency injection
- **Storage**: Room Database
- **Networking**: Retrofit, OkHttp
- **AI**: Google Generative AI SDK

## Future Architecture Considerations

- **Modularization**: Splitting into feature modules for better separation
- **Testing Infrastructure**: Comprehensive test coverage
- **Analytics Integration**: Understanding user patterns
- **Plugin System**: Extensible architecture for new capabilities 