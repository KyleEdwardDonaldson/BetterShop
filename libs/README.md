# Libraries Folder

This folder contains optional third-party plugin dependencies for compilation.

## Towns and Nations Integration

To enable Towns and Nations integration:

1. Download `TownsAndNations-0.15.4.jar` from:
   https://github.com/Leralix/Towns-and-Nations/releases/tag/v0.15.4

2. Place it in this folder as:
   ```
   libs/TownsAndNations-0.15.4.jar
   ```

3. Build the project:
   ```bash
   mvn clean package
   ```

The build will automatically detect the JAR and include TaN support.

## Without TownsAndNations JAR

If the JAR is not present, the plugin will build successfully but without TaN integration classes. The plugin will still work with Towny integration.
