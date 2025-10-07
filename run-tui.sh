#!/bin/bash
# Run the Interactive TUI version
if [ ! -f "target/jpassman-1.0.jar" ]; then
    echo "JAR not found. Please run ./build.sh first."
    exit 1
fi
java -cp target/jpassman-1.0.jar com.jpassman.TuiPasswordManager
