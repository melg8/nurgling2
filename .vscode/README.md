# Nurgling 2 - IDE Setup

## Requirements

- **Java 21 or later** (Java 25 recommended)
  - Download from: https://adoptium.net/
  - Eclipse Adoptium Temurin JDK recommended
- **Ant 1.10+** (usually bundled with JDK)

## VSCode Setup

### Required Extensions
- Language Support for Java(TM) by Red Hat
- Debugger for Java
- Java Test Runner (optional)

### Configuration
The project is pre-configured with:
- Java 21 source/target compatibility
- LWJGL 3.3.3 + JOGL libraries
- Required JVM flags for Java 21+

### Running from VSCode
1. Press `F5` or use "Run and Debug" → "Launch Nurgling"
2. Or use Terminal: `ant bin` then run `bin/play.bat`

### Build Tasks (Ctrl+Shift+B)
- `Ant: Build Client` - Compile source code
- `Ant: Build JAR` - Build hafen.jar
- `Ant: Build BIN` - Full build with all libraries
- `Ant: Clean` - Clean build artifacts

## Eclipse Setup

### Required Configuration
1. Right-click project → Properties
2. Java Build Path → Libraries
3. Ensure JRE System Library is "JavaSE-21" or higher

### Running from Eclipse
1. Right-click `haven.MainFrame` → Run As → Java Application
2. VM Arguments (required):
   ```
   --add-exports=java.base/java.lang=ALL-UNNAMED
   --add-exports=java.desktop/sun.awt=ALL-UNNAMED
   --add-exports=java.desktop/sun.java2d=ALL-UNNAMED
   --enable-native-access=ALL-UNNAMED
   ```

### Build with Ant
1. Window → Show View → Ant
2. Drag `build.xml` to Ant view
3. Double-click targets to build

## Troubleshooting

### "UnsupportedClassVersionError"
- Make sure you're using Java 21+, not Java 8
- Check `JAVA_HOME` environment variable
- In VSCode: `"java.configuration.runtimes"` setting

### "Could not find or load main class"
- Run `ant bin` first to build the project
- Check that all library JARs exist in `lib/ext/`

### JOGL/OpenGL errors
- Ensure `--add-exports` flags are set
- Update graphics drivers
- Try software rendering: `-Dsun.java2d.opengl=false`

### Library not found errors
- Run `ant build-env` to download dependencies
- Or manually place JARs in `lib/ext/` folders

## Project Structure

```
nurgling2/
├── src/                    # Source code
├── build/                  # Build output
│   ├── classes/           # Compiled classes
│   ├── hafen.jar          # Main JAR
│   └── nurgling-res.jar   # Nurgling resources
├── bin/                   # Distribution folder
│   ├── hafen.jar         # Main JAR
│   ├── *.jar             # Libraries
│   └── play.bat          # Launch script
├── lib/
│   ├── jglob.jar
│   └── ext/              # External libraries
│       ├── jogl/         # JOGL (OpenGL bindings)
│       ├── lwjgl/        # LWJGL 3 (OpenGL bindings)
│       └── steamworks/   # Steam API
├── etc/                   # Configuration and resources
└── build.xml             # Ant build script
```

## Quick Start

```bash
# Build the project
ant bin

# Run the game
bin\play.bat
```
