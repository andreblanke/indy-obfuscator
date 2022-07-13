#!/usr/bin/env sh
#
# Tests whether the obfuscator can obfuscate itself.

JAR_ARTIFACT_PATH='target/indy-obfuscator-1.0-SNAPSHOT.jar'
OBF_ARTIFACT_PATH='target/indy-obfuscator-1.0-SNAPSHOT.obf.jar'

if [ ! -f "$(dirname "$0")/$JAR_ARTIFACT_PATH" ]; then
  mvn package
fi

java -jar "$JAR_ARTIFACT_PATH" "$JAR_ARTIFACT_PATH" -o "$OBF_ARTIFACT_PATH" \
    -I dev\.blanke\.indyobfuscator\..* \
    --bsm-template 'native/bootstrap.c.ftl' > native/bootstrap.c

cd native/cmake/
if [ ! -f 'Makefile' ]; then
  cmake -DCMAKE_BUILD_TYPE=Debug ..
fi
make
mv libbootstrap.so ../../

cd ../../
java -jar "$OBF_ARTIFACT_PATH"
