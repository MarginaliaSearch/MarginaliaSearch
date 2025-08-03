#include "cpphelpers.hpp"
#include <algorithm>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <libaio.h>

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

static io_context_t ctx = 0;

int aio_read(int fd, int n, void** buffers, unsigned int* sizes, long* offsets) {
    if (ctx == 0 && io_setup(1024, &ctx) != 0) {
        perror("io_setup failed");
        return -1;  // Setup failed
    }

//    printf("aio_read(%d,%d,%d)\n", fd, n, offsets[0]);
//    fflush(stdout);

    struct iocb* iocbs[1024];
    struct iocb ios[1024];

    for (int i = 0; i < n; i++) {
        io_prep_pread(&ios[i], fd, buffers[i], sizes[i], offsets[i]);
        ios[i].data = (void*)(long)i;
        iocbs[i] = &ios[i];
    }

    int ret = io_submit(ctx, n, iocbs);
    if (ret != n) {
        perror("AIO Read failed");
        fprintf(stderr, "%d %d %p %p %p", fd, n, buffers, sizes, offsets);
        return -1;
    }

    struct io_event events[n];
    int completed = io_getevents(ctx, n, n, events, NULL);
    for (int i = 0; i < completed; i++) {
        struct io_event* event = &events[i];
        int buffer_index = (int)(long)event->data;

        if (event->res <= 0) {
            return -1;
        }
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