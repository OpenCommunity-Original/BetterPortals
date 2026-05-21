# Developer Guide

This guide is for developers working on the BetterPortals codebase, introducing compilation commands, testing guidelines, debugging procedures, and VS Code integrations.

---

## 🛠️ Compilation & Packaging

BetterPortals uses Gradle 8.x with a multi-module configuration. All builds should be performed via the project's root folder.

### Common Gradle Tasks

* **Clean Build Output:**
  ```powershell
  .\gradlew.bat clean
  ```
* **Compile and Shaded Assembly:**
  Generates the final multi-platform shaded JAR.
  ```powershell
  .\gradlew.bat shadowJar
  ```
* **Run Unit Tests:**
  Runs JUnit 5 test suites.
  ```powershell
  .\gradlew.bat test
  ```

---

## 🧪 Testing & Code Verification

Tests are written using **JUnit 5**. When writing or editing code, verify changes by executing `.\gradlew.bat test`.

### Test Locations
* Core shared stream tests: `[EncryptionTests](../shared/src/test/java/EncryptionTests.java)` and `[EncryptedObjectStreamTests](../shared/src/test/java/EncryptedObjectStreamTests.java)`.
* Bukkit component mocks: `[bukkit/src/test/java](../bukkit/src/test/java/)`.
* Block update test managers: `[TestMultiBlockChangeManager](../bukkit/src/test/java/implementations/TestMultiBlockChangeManager.java)`.

---

## 🐞 Debugging with VS Code

We have set up VS Code `.vscode/tasks.json` and `.vscode/launch.json` configuration files to support debugging and building.

### 1. Build and Shading
Press `Ctrl+Shift+B` or open the VS Code Command Palette and run `Tasks: Run Build Task` -> select **Gradle ShadowJar**. This automatically compiles all modules and places the final shaded JAR in `final/build/libs/`.

### 2. Live Debug Attachment
You can attach your VS Code debugger directly to a running Paper or Velocity server:
1. Start your PaperMC or Velocity process with JVM debug flags enabled:
   ```bash
   java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar paper.jar
   ```
2. In VS Code, go to the **Run & Debug** tab (`Ctrl+Shift+D`).
3. Select **Debug Paper (Attach 5005)** or **Debug Proxy (Attach 5006)**.
4. Click the Play button or press **F5** to start debugging. You can now set breakpoints and inspect variables in real-time.

---

## ⚙️ Adding Packets or Rotations

When adding or modifying portal behavior:

### Packet Manipulation
All ProtocolLib modifications (such as scaling coordinates, rotating velocities, or sending block changes) should reside in **`[PacketUtil](../bukkit/src/main/java/com/lauriethefish/betterportals/bukkit/nms/PacketUtil.java)`**. Avoid constructing or sending packets directly inside business logic classes.

### Math & Coordinates
* Vector rotations and offsets: Use `[PortalTransformations](../bukkit/src/main/java/com/lauriethefish/betterportals/bukkit/portal/PortalTransformations.java)`.
* Matrix calculation utility logic: Use `[RotationUtil](../bukkit/src/main/java/com/lauriethefish/betterportals/bukkit/nms/RotationUtil.java)`.
* Modern block material NMS checks: Use `[MaterialUtil](../bukkit/src/main/java/com/lauriethefish/betterportals/bukkit/util/MaterialUtil.java)`.
