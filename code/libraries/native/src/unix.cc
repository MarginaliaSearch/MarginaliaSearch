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
void fadvise_willneed_range(int fd, long offset, long size) {
  posix_fadvise(fd, offset, size, POSIX_FADV_WILLNEED);
}
void madvise_random(void* address, unsigned long size) {
  madvise(address, size, MADV_RANDOM);
}
void madvise_willneed(void* address, unsigned long size) {
  madvise(address, size, MADV_WILLNEED);
}
void madvise_normal(void* address, unsigned long size) {
  madvise(address, size, MADV_NORMAL);
}

// Whether the page holding the address is resident in the page cache.
// Returns 1 if resident, 0 if not, -1 on error.
int page_resident(void* address) {
  unsigned char vec;
  void* page = (void*) ((unsigned long) address & ~4095UL);
  if (mincore(page, 1, &vec) != 0) {
    return -1;
  }
  return vec & 1;
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