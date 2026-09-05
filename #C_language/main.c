#ifndef _WIN32
#define _POSIX_C_SOURCE 199309L
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "sorters.h"
#include "recorder.h"

/* =====================================================================
 * Manual switches - edit these before each run, same idea as the
 * constants at the top of BenchmarkRunner.java.
 * ===================================================================== */

/* true  -> append new trials after whatever is already in the csv files
 * false -> old behavior, always start from Trial 1 (overwrites existing trials) */
#define APPEND 0

/* 1 -> save every generated random array into RandomValues_Dataset.csv
 * 0 -> skip it (smaller/faster runs) */
#define SAVE_RANDOM_DATASET 0

/* How many trials to run this time (new trials if APPEND=1, total trials if APPEND=0). */
#define NUM_NEW_TRIALS 20

/* This run writes to the csv folder for n-element arrays. */
#define CURRENT_N 500

/* Fixed base seed so every (size, trial) pair always produces the same random array. */
#define BASE_SEED 42LL

/* How many extra untimed sort()+reverseSort() calls to run per algorithm before
 * Trial 1. NOTE: unlike the Java version, C has no JIT - the binary is already
 * fully compiled machine code from the start, so there's no JIT warm-up bias to
 * remove. This loop is kept anyway to warm the CPU cache / branch predictor and
 * to keep the experimental procedure symmetric with the Java version, but the
 * justification is different - mention this if it comes up in your report. */
#define WARMUP_ITERATIONS 5

/* =====================================================================
 * High-resolution timer (portable: Windows / POSIX)
 * ===================================================================== */

#ifdef _WIN32
#include <windows.h>
static double now_ns(void) {
    static LARGE_INTEGER freq;
    static int initialized = 0;
    if (!initialized) {
        QueryPerformanceFrequency(&freq);
        initialized = 1;
    }
    LARGE_INTEGER t;
    QueryPerformanceCounter(&t);
    return (double)t.QuadPart * 1e9 / (double)freq.QuadPart;
}
#else
static double now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double)ts.tv_sec * 1e9 + (double)ts.tv_nsec;
}
#endif

/* =====================================================================
 * Deterministic RNG (xorshift64) - NOT the same generator as Java's
 * java.util.Random, but reproducible within this program: the same
 * (size, trial) pair always yields the exact same array, run after run.
 * ===================================================================== */

static unsigned long long rngState;

static void rng_seed(long long seed) {
    rngState = (unsigned long long)seed;
    if (rngState == 0) {
        rngState = 0x9E3779B97F4A7C15ULL;
    }
}

static unsigned int rng_next(void) {
    rngState ^= rngState << 13;
    rngState ^= rngState >> 7;
    rngState ^= rngState << 17;
    return (unsigned int)(rngState & 0x7FFFFFFFu);
}

/* seed = BASE_SEED + size*1000 + trial -> same (size, trial) always gives the same array. */
static int *generate_random_array(int size, int trial) {
    long long seed = BASE_SEED + (long long)size * 1000LL + trial;
    rng_seed(seed);
    int *arr = (int *)malloc(sizeof(int) * (size_t)size);
    for (int i = 0; i < size; i++) {
        arr[i] = (int)(rng_next() % (unsigned int)size);
    }
    return arr;
}

static int compare_ints(const void *a, const void *b) {
    return (*(const int *)a) - (*(const int *)b);
}

/* Built-in qsort() is used ONLY to build the "already sorted" ground-truth
 * input for the Sorted/ReverseSorted test cases - it is never one of the 5
 * measured algorithms, matching how Arrays.sort() was used in the Java version. */
static int *sorted_copy_ascending(const int *arr, int n) {
    int *copy = (int *)malloc(sizeof(int) * (size_t)n);
    memcpy(copy, arr, sizeof(int) * (size_t)n);
    qsort(copy, (size_t)n, sizeof(int), compare_ints);
    return copy;
}

static int *clone_array(const int *arr, int n) {
    int *copy = (int *)malloc(sizeof(int) * (size_t)n);
    memcpy(copy, arr, sizeof(int) * (size_t)n);
    return copy;
}

static double time_sort(SortFunc f, int **arrPtr, int n) {
    double start = now_ns();
    f(arrPtr, n);
    double elapsedNanos = now_ns() - start;
#if TIME_UNIT == TIME_UNIT_MS
    return elapsedNanos / 1000000.0;
#else
    return elapsedNanos;
#endif
}

static void warm_up(int size) {
    printf("Warming up (%d untimed iterations per algorithm)...\n", WARMUP_ITERATIONS);
    for (int a = 0; a < NUM_ALGS; a++) {
        for (int i = 1; i <= WARMUP_ITERATIONS; i++) {
            /* negative trial numbers so warm-up arrays never collide with real trials */
            int *warmOriginal = generate_random_array(size, -i);
            int *warmAscending = sorted_copy_ascending(warmOriginal, size);

            int *c1 = clone_array(warmOriginal, size);
            ALGORITHMS[a].ascending(&c1, size);
            free(c1);

            int *c2 = clone_array(warmAscending, size);
            ALGORITHMS[a].ascending(&c2, size);
            free(c2);

            int *c3 = clone_array(warmAscending, size);
            ALGORITHMS[a].descending(&c3, size);
            free(c3);

            free(warmOriginal);
            free(warmAscending);
        }
    }
    printf("Warm-up complete.\n");
}

int main(void) {
    char dir[512];
    snprintf(dir, sizeof(dir), "csv/%d(n)", CURRENT_N);

    DatasetRecorder *rec = recorder_open(dir, CURRENT_N, SAVE_RANDOM_DATASET);

    int startTrial = APPEND ? recorder_get_next_trial(rec) : 1;
    int endTrial = startTrial + NUM_NEW_TRIALS - 1;
    printf("%s - Trial %d through Trial %d\n",
           APPEND ? "Appending" : "Overwriting from start", startTrial, endTrial);

    warm_up(CURRENT_N);

    for (int trial = startTrial; trial <= endTrial; trial++) {
        int *original = generate_random_array(CURRENT_N, trial);
        int *ascendingInput = sorted_copy_ascending(original, CURRENT_N);

        if (SAVE_RANDOM_DATASET) {
            recorder_record_random_array(rec, original, CURRENT_N, trial);
        }

        for (int a = 0; a < NUM_ALGS; a++) {
            /* RANDOM case: ascending-sort a fresh copy of the random array */
            int *copy1 = clone_array(original, CURRENT_N);
            double randomNanos = time_sort(ALGORITHMS[a].ascending, &copy1, CURRENT_N);
            recorder_record_timing(rec, CASE_RANDOM, a, trial, randomNanos);
            free(copy1);

            /* SORTED case: ascending-sort an array that's already ascending (best case) */
            int *copy2 = clone_array(ascendingInput, CURRENT_N);
            double sortedNanos = time_sort(ALGORITHMS[a].ascending, &copy2, CURRENT_N);
            recorder_record_timing(rec, CASE_SORTED, a, trial, sortedNanos);
            free(copy2);

            /* REVERSE_SORTED case: descending-sort an array that's already ascending (worst case) */
            int *copy3 = clone_array(ascendingInput, CURRENT_N);
            double reverseNanos = time_sort(ALGORITHMS[a].descending, &copy3, CURRENT_N);
            recorder_record_timing(rec, CASE_REVERSE_SORTED, a, trial, reverseNanos);
            free(copy3);
        }

        free(original);
        free(ascendingInput);
        printf("Size %d, trial %d/%d done.\n", CURRENT_N, trial, endTrial);
    }

    recorder_save_all(rec);
    recorder_close(rec);

    printf("All csv files updated in %s/\n", dir);
    return 0;
}
