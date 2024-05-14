#!/usr/bin/env sh

if ! which g++ > /dev/null; then
    echo "g++ not found, skipping compilation"
    exit 0
fi

c++ -c -O3 -march=native -shared -fPIC -Isrc/main/public src/main/cpp/*.cpp -o resources/libcpp.so