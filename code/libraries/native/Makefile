#!/usr/bin/env sh

CXXFLAGS=-O3 -march=native -std=c++14 -fPIC `pkg-config --cflags liburing`
LDFLAGS=

# Weird hack to get liburing to link in prod
# don't @ me
LIBURING_PATH=`./findliburing.sh`

CXX=c++

HEADERS=src/config.h
SOURCES=src/sort.cc src/unix.cc src/uring.cc

all: resources/libcpp.so

resources/libcpp.so: ${SOURCES} ${HEADERS} resources/liburing.so
	${CXX} -shared ${CXXFLAGS} ${SOURCES} resources/liburing.so -o resources/libcpp.so
resources/liburing.so: findliburing.sh
	cp ${LIBURING_PATH} resources/liburing.so
clean:
	rm -rf resources/{libcpp,liburing}.so