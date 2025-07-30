#include <stdint.h>

#pragma once

extern "C" {
  void ms_sort_64(int64_t* area, uint64_t start, uint64_t end);
  void ms_sort_128(int64_t* area, uint64_t start, uint64_t end);

  int open_direct_fd(char* filename);
  int read_at(int fd, void* buf, unsigned int count, long offset);
  void close_fd(int fd);
}
