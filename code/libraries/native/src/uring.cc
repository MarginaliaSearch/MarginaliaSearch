#include "config.h"

#include <algorithm>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#ifndef NO_IO_URING
#include <liburing.h>
#endif
#include <string.h>

extern "C" {

#ifndef NO_IO_URING

io_uring* initialize_uring_single_file(int queue_size, int fd) {
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

	// Register the file descriptor with io_uring to speed it up fairly significantly
	int *fds = (int*) malloc(sizeof(int));

	fds[0] = fd;
	ret = io_uring_register_files(ring, fds, 1);

	if (ret < 0) {
		fprintf(stderr, "io_uring_register_files failed: %s\n", strerror(-ret));
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


// Register a single buffer with the ring, so that reads prepared with
// read_fixed skip the per-operation buffer import and validation
int uring_register_buffer(io_uring* ring, void* addr, unsigned long len) {
    struct iovec iov = { addr, len };
    return io_uring_register_buffers(ring, &iov, 1);
}

// Like uring_read_buffered, but all target buffers must fall within the
// ring's registered buffer
int uring_read_fixed(io_uring* ring, int n, void** buffers, unsigned int* sizes, long* offsets) {
    for (int i = 0; i < n; i++) {
        struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
        if (!sqe) {
            fprintf(stderr, "uring_queue full!");
            return -1;
        }

        io_uring_prep_read_fixed(sqe, 0, buffers[i], sizes[i], offsets[i], 0);
        sqe->flags |= IOSQE_FIXED_FILE;
        io_uring_sqe_set_data(sqe, (void*)(long)i);
    }

    int submitted = io_uring_submit_and_wait(ring, n);
    if (submitted != n) {
        fprintf(stderr, "io_uring_submit(): submitted != %d, was %d", n, submitted);
        return -1;
    }

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

int uring_read_buffered(io_uring* ring, int n, void** buffers, unsigned int* sizes, long* offsets) {

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

        io_uring_prep_read(sqe, 0, buffers[i], sizes[i], offsets[i]);
        sqe->flags |= IOSQE_FIXED_FILE;
        io_uring_sqe_set_data(sqe, (void*)(long)i);
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


#endif

}