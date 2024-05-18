#include <stdint.h>

#pragma once

extern "C" {
  void ms_sort_64(int64_t* area, uint64_t start, uint64_t end);
  void ms_sort_128(int64_t* area, uint64_t start, uint64_t end);
}
