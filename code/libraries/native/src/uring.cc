#include <algorithm>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <liburing.h>
#include <string.h>

extern "C" {
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


int uring_read_submit_and_poll(
						io_uring* ring,
                        long* result_ids,
                        int in_flight_requests,
                        int read_count,
                        long* read_batch_ids,
                        int* read_fds,
                        void** read_buffers,
                        unsigned int* read_sizes,
                        long* read_offsets)
{

    for (int i = 0; i < read_count; i++) {
        struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
        if (!sqe) {
            fprintf(stderr, "uring_queue full!");
            return -1;
        }

        io_uring_prep_read(sqe, read_fds[i], read_buffers[i], read_sizes[i], read_offsets[i]);
        io_uring_sqe_set_data(sqe, (void*) read_batch_ids[i]);
    }

	int wait_cnt = 8;

	if (wait_cnt > in_flight_requests) {
		wait_cnt = in_flight_requests;
	}

    int submitted = io_uring_submit_and_wait(ring, wait_cnt);
    if (submitted != read_count) {
    	if (submitted < 0) {
    		fprintf(stderr, "io_uring_submit %s\n", strerror(-submitted));
    	}
    	else {
        	fprintf(stderr, "io_uring_submit(): submitted != %d, was %d", read_count, submitted);
		}
        return -1;
    }

	int completed = 0;
	struct io_uring_cqe *cqe;
	while (io_uring_peek_cqe(ring, &cqe) == 0) {
        if (cqe->res < 0) {
            fprintf(stderr, "io_uring error: %s\n", strerror(-cqe->res));
			result_ids[completed++] = -cqe->user_data; // flag an error by sending a negative ID back so we can clean up memory allocation etc
        }
		else {
        	result_ids[completed++] = cqe->user_data;
        }
        io_uring_cqe_seen(ring, cqe);
    }

    return completed;
}

int uring_poll(io_uring* ring, long* result_ids)
{
	int completed = 0;
	struct io_uring_cqe *cqe;
	while (io_uring_peek_cqe(ring, &cqe) == 0) {
        if (cqe->res < 0) {
            fprintf(stderr, "io_uring error: %s\n", strerror(-cqe->res));
			result_ids[completed++] = -cqe->user_data; // flag an error by sending a negative ID back so we can clean up memory allocation etc
        }
		else {
        	result_ids[completed++] = cqe->user_data;
        }
        io_uring_cqe_seen(ring, cqe);
    }

    return completed;
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
}