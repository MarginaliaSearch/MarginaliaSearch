#include "cpphelpers.hpp"
#include <algorithm>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <liburing.h>
#include <cstring>
#include <sys/mman.h>
extern "C" {
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

void fadvise_random(int fd) {
  posix_fadvise(fd, 0, 0, POSIX_FADV_RANDOM);
}
void madvise_random(void* address, unsigned long size) {
  madvise(address, size, MADV_RANDOM);
}
io_uring* initialize_uring(int queue_size) {
    io_uring* ring = (io_uring*) malloc(sizeof(io_uring));
    if (!ring) return NULL;

    int ret = io_uring_queue_init(queue_size, ring, 0);
    if (ret < 0) {
        fprintf(stderr, "io_uring_queue_init failed: %s\n", strerror(-ret));
        if (-ret == ENOMEM) {
            fprintf(stderr, "If you are seeing this error, you probably need to increase `ulimit -l` or memlock in /etc/security/limits.conf");
        }
        free(ring);
        return NULL;
    }

    fprintf(stderr, "Initialized ring @ %p (sq=%u, cq=%u)\n",
            ring, ring->sq.ring_entries, ring->cq.ring_entries);
    return ring;
}

void close_uring(io_uring* ring) {
    fprintf(stderr, "Closed ring @ %p\n", ring);
    io_uring_queue_exit(ring);
    free(ring);
}

int uring_read_buffered(int fd, io_uring* ring, int n, void** buffers, unsigned int* sizes, long* offsets) {

#ifdef DEBUG_CHECKS

    struct stat st;
    fstat(fd, &st);
    for (int i = 0; i < n; i++) {
        if (offsets[i] + sizes[i] > st.st_size) {
            fprintf(stderr, "Read beyond EOF: offset %ld >= size %ld\n",
                    offsets[i], st.st_size);
            return -1;
        }
    }
#endif

    unsigned ready = io_uring_cq_ready(ring);
    if (ready > 0) {
        fprintf(stderr, "Skipping %u leftover completions\n", ready);
        io_uring_cq_advance(ring, ready);
    }
    for (int i = 0; i < n; i++) {
        struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
        if (!sqe) {
            fprintf(stderr, "uring_queue full!");
            return -1;
        }

        io_uring_prep_read(sqe, fd, buffers[i], sizes[i], offsets[i]);
        io_uring_sqe_set_data(sqe, (void*)(long)i);  // Store buffer index
    }

    int submitted = io_uring_submit_and_wait(ring, n);
    if (submitted != n) {
        fprintf(stderr, "io_uring_submit(): submitted != %d, was %d", n, submitted);
        return -1;
    }
    int completed = 0;
    int bad = 0;
    for (int i = 0; i < n; i++) {
        struct io_uring_cqe *cqe;
        int ret = io_uring_wait_cqe(ring, &cqe);
        if (ret < 0) {
            fprintf(stderr, "io_uring_wait_cqe failed: %s\n", strerror(-ret));
            return -1;
        }

        if (cqe->res < 0) {
            fprintf(stderr, "io_uring error: %s\n", strerror(-cqe->res));
        }
        io_uring_cqe_seen(ring, cqe);
    }

    return n;
}


int uring_read_direct(int fd, io_uring* ring, int n, void** buffers, unsigned int* sizes, long* offsets) {
#ifdef DEBUG_CHECKS
    if (!ring) {
        fprintf(stderr, "NULL ring!\n");
        return -1;
    }
    if (!buffers || !sizes || !offsets) {
        fprintf(stderr, "NULL arrays: buffers=%p sizes=%p offsets=%p\n",
                buffers, sizes, offsets);
        return -1;
    }
    for (int i = 0; i < n; i++) {
        if (((uintptr_t)buffers[i] & 511) != 0) {
            fprintf(stderr, "Buffer %d not aligned to 512 bytes, is %p\n", i, buffers[i]);
            return -1;
        }
    }

    struct stat st;
    fstat(fd, &st);
    for (int i = 0; i < n; i++) {
        if (offsets[i] + sizes[i] >= st.st_size) {
            fprintf(stderr, "Read beyond EOF: offset %ld >= size %ld\n",
                    offsets[i], st.st_size);
            return -1;
        }
    }
#endif

    unsigned ready = io_uring_cq_ready(ring);
    if (ready > 0) {
        fprintf(stderr, "Skipping %u leftover completions\n", ready);
        io_uring_cq_advance(ring, ready);
    }

    for (int i = 0; i < n; i++) {
        struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
        if (!sqe) {
            fprintf(stderr, "uring_queue full!");
            return -1;
        }

        io_uring_prep_read(sqe, fd, buffers[i], sizes[i], offsets[i]);
        io_uring_sqe_set_data(sqe, (void*)(long)i);  // Store buffer index
    }

    int submitted = io_uring_submit_and_wait(ring, n);
    if (submitted != n) {
        fprintf(stderr, "io_uring_submit(): submitted != %d, was %d", n, submitted);
        return -1;
    }
    int completed = 0;
    int bad = 0;
    for (int i = 0; i < n; i++) {
        struct io_uring_cqe *cqe;
        int ret = io_uring_wait_cqe(ring, &cqe);
        if (ret < 0) {
            fprintf(stderr, "io_uring_wait_cqe failed: %s\n", strerror(-ret));
            return -1;
        }

        if (cqe->res < 0) {
            fprintf(stderr, "io_uring error: %s\n", strerror(-cqe->res));
        }
        io_uring_cqe_seen(ring, cqe);
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

}