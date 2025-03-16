# AI Chat Bot Agent

## Overview
AI Chat Bot Agent is a personal assistant application that integrates advanced AI capabilities with automated interaction features. The app serves as a bridge between users and their devices, allowing for efficient message sending, app control, and information retrieval through natural language commands.

## Key Features

### 1. Multi-Platform Messaging
- Send messages across multiple platforms (WhatsApp, SMS, Telegram) with simple voice or text commands
- Automatic platform detection based on contact availability
- Intelligent contact search and selection

### 2. AI Command Processing
- Natural language command understanding via Google's Gemini AI
- Contextual responses based on command success/failure
- Adaptive platform selection without explicit specification

### 3. Accessibility Automation
- Automated UI navigation and interaction
- Text input and button clicking across applications
- Support for complex UI flows and error handling

### 4. Visual Monitoring System
- Floating eye widget that shows real-time operation feedback
- Visual indicators for ongoing operations
- Detailed operation logging and status tracking

## How It Works

The AI Chat Bot Agent uses a combination of:
- **Accessibility Services** to interact with other apps' UI elements
- **Google's Gemini AI API** for natural language understanding
- **Room Database** for persistent chat history
- **Android Services** for background operation

## Getting Started

1. **Install the App**
   - Download and install from Google Play or build from source
   - Grant necessary permissions during initial setup

2. **Enable Accessibility Service**
   - Navigate to Settings → Accessibility → AI Chat Bot Agent
   - Toggle the service ON

3. **Using the App**
   - Open the AI Agent screen
   - Type or speak your command (e.g., "Send a WhatsApp message to John saying I'll be late")
   - The app will process your request and execute the appropriate actions

4. **Monitoring Operations**
   - Toggle the "Monitor" switch to enable the floating eye widget
   - Watch as the app navigates through different UI elements
   - Tap the eye to see detailed information about current operations

## Version History

### v2.0.0 (Current)
- Added multi-platform messaging support (WhatsApp, SMS, Telegram)
- Implemented real-time operation monitoring with visual feedback
- Enhanced AI command understanding with Gemini API
- Improved error handling and user feedback
- Updated UI with Material Design components

### v1.0.0
- Initial release with basic WhatsApp messaging support
- Simple command processing for sending messages
- Basic accessibility service integration

## Permissions Required
- Accessibility Service (for UI automation)
- Overlay Permission (for floating eye widget)
- Internet (for AI processing)
- Notification Access (for operation monitoring)

## Technical Documentation
For developers interested in understanding or contributing to the project, please refer to the detailed technical documentation in the [`/docs`](docs) folder.

## Privacy
This application does not collect or store any personal data beyond what is necessary for its operation. All message content is processed locally and is not transmitted to any third-party servers except for the AI processing via Google's Gemini API.

## Support
For issues, questions, or feature requests, please open an issue in this repository or contact support at maurya972137@gmail.com.com

## License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details. 
