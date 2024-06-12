#!/usr/bin/env sh

CXX=${CXX:-g++}

if ! which ${CXX} > /dev/null; then
    echo "g++ not found, skipping compilation"
    exit 0
fi

${CXX} -O3 -march=native -std=c++14 -shared -Isrc/main/public src/main/cpp/*.cpp -o resources/libcpp.so
