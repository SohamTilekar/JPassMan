#!/bin/bash
# A native fallback build script if Maven is not installed.
echo "Building JPassMan without Maven..."

# Clean previous build
rm -rf target/
mkdir -p target/classes

# Compile sources
javac -d target/classes src/main/java/com/jpassman/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

# Create Manifest
cat <<EOF > target/MANIFEST.MF
Manifest-Version: 1.0
Main-Class: com.jpassman.TuiPasswordManager
EOF

# Package Jar
jar cfm target/jpassman-1.0.jar target/MANIFEST.MF -C target/classes .

echo "Build successful! Created target/jpassman-1.0.jar"
