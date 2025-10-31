# Code Guru Scanner Worker

Scanner Worker is an IntelliJ Plugin for Code Guru that analyzes Java code structure and sends the data to a remote service via gRPC.

## Features

- Scans Java files in your project to extract code structure information
- Analyzes class and method relationships
- Collects method dependencies and references
- Sends code structure data to a remote service via gRPC

## Development

### Prerequisites

- IntelliJ IDEA
- Java 17 or higher
- Gradle

### Building

```bash
./gradlew build
```

### Running

1. Open the project in IntelliJ IDEA
2. Run the plugin using the Gradle `runIde` task

```bash
./gradlew generateProto
```

## License

This project is licensed under the GNU General Public License v3.0 - see the LICENSE file for details.
