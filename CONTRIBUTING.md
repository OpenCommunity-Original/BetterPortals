# Contributing to BetterPortals

Thank you for your interest in contributing to BetterPortals! We welcome community contributions, bug fixes, performance optimizations, and feature enhancements.

Targeting Minecraft 1.21+ and Paper 26.1.2 means we focus on modern Java (Java 17/21), clean APIs, and asynchronous operations.

---

## Guidelines for Contributions

### 1. Code Standards and Architecture
- Maintain clean, readable code. Avoid duplication and spaghetti blocks.
- Centralize packet alterations and transmissions in `PacketUtil` and coordinate logic in `RotationUtil`/`PortalTransformations`.
- Ensure new methods are fully documented and resource allocations (such as thread pools or sockets) are cleaned up correctly to avoid memory or socket leaks.

### 2. Testing and Validation
- Verify your changes by running the automated unit test suite before opening a PR:
  ```powershell
  .\gradlew.bat test
  ```
- If your change affects a new area or component, write corresponding JUnit tests.
- Manually test your code on a local Paper/Velocity server instance to ensure player movement, portal rendering, and teleportation are working correctly.

### 3. Creating a Pull Request
- **Small changes / Bug fixes:** Feel free to open a PR directly. Please include a clear description of the bug and your fix.
- **Large architectural changes:** Please open an issue first to discuss your design proposal. This prevents wasted effort if changes align poorly with the project path.

---

## Developer Reference Materials
Before jumping into the code, we highly recommend reading the following documents:
- 🏗️ **[Project Structure](docs/project_structure.md):** Explains how modules (shared, bukkit, proxy, etc.) are organized and relate.
- 💻 **[Developer Guide](docs/developer_guide.md):** Build commands, test suites, and remote debugging with VS Code.
- 🔌 **[Networking Protocol](docs/networking_protocol.md):** Explains the cross-server packet relay system and the encryption layer.
