#include <algorithm>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/uio.h>
#include <cerrno>
#include <cstdint>

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

long read_vectored_at(int fd, const struct iovec* iov, int count, long offset) {
    return preadv(fd, iov, count, offset);
}

void close_fd(int fd) {
    close(fd);
}

int buffered_bulk_read(const int fd,
                       const int32_t n,
                       void **const destinations,
                       const uint32_t* lengths,
                       const int64_t* offsets)
{
    if (n < 0 || n > 16) {
        fprintf(stderr, "Invalid item count %d\n", n);
        errno = E2BIG;
        return -1;
    }
    uint8_t success[16];

    int read = 0;

    for (int32_t idx = 0; idx < n; ++idx) {
        if (success[idx] = lengths[idx] == 0) {
            ++read;
            continue;
        }

        iovec iov { destinations[idx], lengths[idx] };
        ssize_t ret = preadv2(fd, &iov, 1, offsets[idx], RWF_NOWAIT);

        if (ret < 0 && errno != EAGAIN && errno != ENOTSUP) {
            // You get ENOTSUP on e.g. tmpfs
            fprintf(stderr, "preadv2 error %d\n", n);
            return -read-1;
        }

        if (ret == lengths[idx]) {
            success[idx] = true;
            ++read;
        }
        else {
            posix_fadvise(fd, offsets[idx], lengths[idx], POSIX_FADV_WILLNEED);
        }
    }

    if (read < n) {
        for (int32_t idx = 0; idx < n; ++idx) {
            if (success[idx])
                continue;

            ssize_t ret = pread(fd, destinations[idx], lengths[idx], offsets[idx]);

            if (ret != lengths[idx]) {
                fprintf(stderr, "pread error %d\n", n);
                return -read-1;
            }

            ++read;
        }
    }

    return read;
}
}