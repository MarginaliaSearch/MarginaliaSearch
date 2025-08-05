#include <stdint.h>
#include <liburing.h>
#pragma once

extern "C" {
  void ms_sort_64(int64_t* area, uint64_t start, uint64_t end);
  void ms_sort_128(int64_t* area, uint64_t start, uint64_t end);

  int open_direct_fd(char* filename);
  int open_buffered_fd(char* filename);
  int read_at(int fd, void* buf, unsigned int count, long offset);
  int uring_read_buffered(int fd, io_uring* ring, int n, void** buffers, unsigned int* sizes, long* offsets);
  int uring_read_direct(int fd, io_uring* ring, int n, void** buffers, unsigned int* sizes, long* offsets);
  void close_fd(int fd);
  io_uring* initialize_uring(int queue_size);
  void close_uring(io_uring* ring);
}
