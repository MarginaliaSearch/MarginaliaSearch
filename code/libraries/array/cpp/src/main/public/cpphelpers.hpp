#include <stdint.h>

#pragma once

extern "C" {
  void ms_sort_64(int64_t* area, uint64_t start, uint64_t end);
  void ms_sort_128(int64_t* area, uint64_t start, uint64_t end);

  int64_t ms_linear_search_64(int64_t key, int64_t* area, uint64_t fromIndex, uint64_t toIndex);
  int64_t ms_linear_search_128(int64_t key, int64_t* area, uint64_t fromIndex, uint64_t toIndex);

  int64_t ms_binary_search_128(int64_t key, int64_t* area, uint64_t fromIndex, uint64_t toIndex);
  int64_t ms_binary_search_64upper(int64_t key, int64_t* area, uint64_t fromIndex, uint64_t toIndex);
}
