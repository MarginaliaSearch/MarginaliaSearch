#include "cpphelpers.hpp"
#include <algorithm>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <liburing.h>
/* Pair of 64-bit integers. */
/* The struct is packed to ensure that the struct is exactly 16 bytes in size, as we need to pointer
   alias on an array of 8 byte longs.  Since structs guarantee that the first element is at offset 0,
   and __attribute__((packed)) guarantees that the struct is exactly 16 bytes in size, the only reasonable
   implementation is that the struct is laid out as 2 64-bit integers.  This assumption works only as
   long as there are at most 2 fields.

   This is a non-portable low level hack, but all this code strongly assumes a x86-64 Linux environment.
   For other environments (e.g. outside of prod), the Java implementation code will have to do.
*/
struct __attribute__((packed)) p64x2 {
    int64_t a;
    int64_t b;
};

void ms_sort_64(int64_t* area, uint64_t start, uint64_t end) {
  std::sort(&area[start], &area[end]);
}

void ms_sort_128(int64_t* area, uint64_t start, uint64_t end) {
  std::sort(
    reinterpret_cast<p64x2 *>(&area[start]),
    reinterpret_cast<p64x2 *>(&area[end]),
    [](const p64x2& fst, const p64x2& snd) {
    return fst.a < snd.a;
  });
}

static struct io_uring ring;
static int ring_initialized = 0;

int uring_read(int fd, int n, void** buffers, unsigned int* sizes, long* offsets) {
    if (!ring_initialized) {
        int ret = io_uring_queue_init(1024, &ring, 0);
        if (ret < 0) {
            fprintf(stderr, "uring initialization failed");
            return -1;
        }
        ring_initialized = 1;
    }

    for (int i = 0; i < n; i++) {
        struct io_uring_sqe *sqe = io_uring_get_sqe(&ring);
        if (!sqe) {
            fprintf(stderr, "uring_queue full!");
            return -1;
        }

        io_uring_prep_read(sqe, fd, buffers[i], sizes[i], offsets[i]);
        io_uring_sqe_set_data(sqe, (void*)(long)i);  // Store buffer index
    }

    int submitted = io_uring_submit(&ring);
    if (submitted != n) return -1;

    for (int i = 0; i < n; i++) {
        struct io_uring_cqe *cqe;
        int ret = io_uring_wait_cqe(&ring, &cqe);
        if (ret < 0) {
            return -1;
        }

        if (cqe->res <= 0) {
            io_uring_cqe_seen(&ring, cqe);
            while (++i < n) {
                struct io_uring_cqe *remaining_cqe;
                if (io_uring_wait_cqe(&ring, &remaining_cqe) == 0) {
                    io_uring_cqe_seen(&ring, remaining_cqe);
                }
            }
            return -1;
        }

        io_uring_cqe_seen(&ring, cqe);
    }

    return n;
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