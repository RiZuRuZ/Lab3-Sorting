#ifndef RECORDER_H
#define RECORDER_H

#include "sorters.h"

#define NUM_CASES 3

/* Choose the timing unit written to CSV: TIME_UNIT_NS or TIME_UNIT_MS. */
#define TIME_UNIT_NS 0
#define TIME_UNIT_MS 1
#ifndef TIME_UNIT
#define TIME_UNIT TIME_UNIT_NS
#endif

typedef enum { CASE_RANDOM = 0, CASE_SORTED = 1, CASE_REVERSE_SORTED = 2 } CaseType;

extern const char *CASE_LABELS[NUM_CASES];     /* "Random", "Sorted", "ReverseSorted" */
extern const char *CASE_FILE_NAMES[NUM_CASES]; /* "Random_Dataset.csv", ... */

/*
 * Manages one input-size folder's worth of csv files (equivalent to one
 * DatasetRecorder instance in the Java version, scoped to one EXCEL_DIR):
 *
 *   Random_Dataset.csv       -> Algorithm ,Input Size(n),Trial 1..N,Average
 *   Sort_Dataset.csv         -> same columns (best case: already-sorted input)
 *   ReverseSort_Dataset.csv  -> same columns (worst case: descending-sort an ascending input)
 *   RAW_Dataset.csv          -> Algorithm ,Case,Input Size(n),Trial 1..N,Average (derived, always rewritten)
 *   RandomValues_Dataset.csv -> Random Value Index,Trial 1..N (optional)
 *   AVG_Random_Dataset.csv       -> InputSize(n),BubbleSort,InsertionSort,SelectionSort,MergeSort,QuickSort (derived)
 *   AVG_Sort_Dataset.csv         -> same columns (derived)
 *   AVG_ReverseSort_Dataset.csv  -> same columns (derived)
 *
 * Since every file lives inside a folder for ONE input size, every row in
 * every file always has the same size - so internally we only need to store
 * times[algorithm][case][trial] plus the (optional) random arrays.
 */
typedef struct {
    char dir[1024];
    int inputSize;

    int numTrials;        /* existing trial columns loaded from Random/Sort/ReverseSort_Dataset.csv on open */
    int trialCapacity;    /* allocated capacity of each times[][] array                                     */
    double *times[NUM_ALGS][NUM_CASES];

    int saveRandomValues;
    int randomValuesTrials;     /* existing trial columns loaded from RandomValues_Dataset.csv on open */
    int randomValuesCapacity;
    int **randomValues;         /* randomValues[trialIndex] = int array of length inputSize */
} DatasetRecorder;

/* Opens (creating if needed) the csv files under dir for the given input size. */
DatasetRecorder *recorder_open(const char *dir, int inputSize, int saveRandomValues);

/* Returns the next unused trial number (existing max found on disk, + 1). */
int recorder_get_next_trial(const DatasetRecorder *rec);

/* Records one timing (nanoseconds) for algIndex (0..NUM_ALGS-1) under the given case and trial. */
void recorder_record_timing(DatasetRecorder *rec, CaseType caseType, int algIndex, int trial, double nanos);

/* Records one generated random array for the given trial (only kept if saveRandomValues was set). */
void recorder_record_random_array(DatasetRecorder *rec, const int *values, int n, int trial);

/* Writes every csv file to disk, computing RAW_Dataset.csv and the 3 AVG_*.csv files along the way. */
void recorder_save_all(DatasetRecorder *rec);

/* Frees all memory owned by the recorder. */
void recorder_close(DatasetRecorder *rec);

#endif
