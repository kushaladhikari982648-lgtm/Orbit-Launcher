//
// Created by maks on 19.06.2023.
//

#define _GNU_SOURCE // we are GNU GPLv3

#include <linux/limits.h>
#include <limits.h>
#include <unistd.h>
#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>
#include <sched.h>
#include <string.h>
#include <stdbool.h>
#include <pthread.h>
#include "bigcoreaffinity.h"

#define FREQ_MAX 256
static pthread_once_t s_affinity_once = PTHREAD_ONCE_INIT;
static cpu_set_t s_bigcore_set;
static bool s_has_valid_mask = false;

void bigcore_format_cpu_path(char* buffer, unsigned int cpu_core) {
    snprintf(buffer, PATH_MAX, "/sys/devices/system/cpu/cpu%i/cpufreq/cpuinfo_max_freq", cpu_core);
}

static void compute_bigcore_set(void) {
    char path_buffer[PATH_MAX];
    char freq_buffer[FREQ_MAX];
    char* discard;
    unsigned long core_freqs[CPU_SETSIZE] = {0};
    unsigned long max_freq = 0;
    unsigned long min_freq = ULONG_MAX;
    unsigned int corecnt = 0;

    for (unsigned int i = 0; i < CPU_SETSIZE; i++) {
        bigcore_format_cpu_path(path_buffer, i);
        int corefreqfd = open(path_buffer, O_RDONLY);
        if (corefreqfd != -1) {
            ssize_t read_count = read(corefreqfd, freq_buffer, FREQ_MAX - 1);
            close(corefreqfd);
            if (read_count > 0) {
                freq_buffer[read_count] = 0;
                unsigned long freq = strtoul(freq_buffer, &discard, 10);
                if (freq > 0) {
                    core_freqs[i] = freq;
                    if (freq > max_freq) max_freq = freq;
                    if (freq < min_freq) min_freq = freq;
                }
            }
            corecnt = i + 1;
        } else if (i >= 16) {
            break;
        }
    }

    if (corecnt == 0 || max_freq == 0 || max_freq == min_freq) {
        return;
    }

    CPU_ZERO(&s_bigcore_set);
    // Include all performance/prime cores (>= 75% of peak core frequency) and exclude LITTLE cores.
    unsigned long threshold = (max_freq * 75UL) / 100UL;
    unsigned int selected = 0;
    for (unsigned int i = 0; i < corecnt; i++) {
        if (core_freqs[i] >= threshold) {
            CPU_SET_S(i, CPU_SETSIZE, &s_bigcore_set);
            selected++;
        }
    }
    if (selected > 0 && selected < corecnt) {
        s_has_valid_mask = true;
        printf("bigcore: selected %u/%u performance cores (peak %lu kHz)\n", selected, corecnt, max_freq);
    }
}

void bigcore_set_affinity() {
    if (getenv("POJAV_BIG_CORE_AFFINITY") == NULL) {
        return;
    }
    pthread_once(&s_affinity_once, compute_bigcore_set);
    if (!s_has_valid_mask) {
        return;
    }
    int result = sched_setaffinity(0, CPU_SETSIZE, &s_bigcore_set);
    if (result != 0) {
        printf("bigcore: setting affinity failed: %s\n", strerror(result));
    }
}
