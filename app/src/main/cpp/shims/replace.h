#ifndef LINUX_DISTRO_TALLOC_REPLACE_H
#define LINUX_DISTRO_TALLOC_REPLACE_H

#define __STDC_WANT_LIB_EXT1__ 1
#define HAVE_INTTYPES_H 1
#define HAVE_CONSTRUCTOR_ATTRIBUTE 1
#define HAVE_GETAUXVAL 1
#define HAVE_STDINT_H 1
#define HAVE_VA_COPY 1
#define HAVE_SYS_AUXV_H 1

#include <errno.h>
#include <inttypes.h>
#include <limits.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MIN(left, right) ((left) < (right) ? (left) : (right))

static inline void talloc_memset_explicit(void *memory, int value, size_t size)
{
    volatile unsigned char *cursor = memory;
    while (size-- > 0) *cursor++ = (unsigned char)value;
}

#define memset_explicit talloc_memset_explicit

#endif
