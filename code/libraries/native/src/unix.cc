#include <algorithm>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <sys/mman.h>

extern "C" {
void fadvise_random(int fd) {
  posix_fadvise(fd, 0, 0, POSIX_FADV_RANDOM);
}
void fadvise_willneed(int fd) {
  posix_fadvise(fd, 0, 0, POSIX_FADV_WILLNEED);
}
void madvise_random(void* address, unsigned long size) {
  madvise(address, size, MADV_RANDOM);
}


int open_buffered_fd(char* filename) {
  return open(filename, O_RDONLY);
}

int open_direct_fd(char* filename) {
  return open(filename, O_DIRECT | O_RDONLY);
}

int read_at(int fd, void* buf, unsigned int count, long offset) {
  return pread(fd, buf, count, offset);
}
void close_fd(int fd) {
  close(fd);
}

}