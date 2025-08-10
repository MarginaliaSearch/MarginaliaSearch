#!/usr/bin/env sh

CXXFLAGS=-O3 -march=native -std=c++14 -fPIC `pkg-config --cflags liburing`
LDFLAGS=`pkg-config --libs liburing` -shared
CXX=c++

SOURCES=src/sort.cc src/unix.cc src/uring.cc

all: resources/libcpp.so resources/liburing.so
resources/libcpp.so:
	${CXX} -shared ${LDFLAGS} ${CXXFLAGS} ${SOURCES} -o resources/libcpp.so
resources/liburing.so:
	cp /usr/lib/liburing.so resources/
clean:
	rm -rf resources/{libcpp,liburing}.so