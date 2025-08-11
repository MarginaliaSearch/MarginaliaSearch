#!/usr/bin/env sh

CXXFLAGS=-O3 -march=native -std=c++14 -fPIC `pkg-config --cflags liburing`
LDFLAGS=

# Weird hack to get liburing to link on one particular debian server
LIBURING_PATH=`pkg-config liburing --keep-system-libs --libs-only-L | cut -c 3- | tr -d \ `/liburing.so

CXX=c++

SOURCES=src/sort.cc src/unix.cc src/uring.cc

all: resources/libcpp.so

resources/libcpp.so: ${SOURCES} resources/liburing.so
	${CXX} -shared ${CXXFLAGS} ${SOURCES} resources/liburing.so -o resources/libcpp.so
resources/liburing.so:
	cp ${LIBURING_PATH} resources/liburing.so
clean:
	rm -rf resources/{libcpp,liburing}.so