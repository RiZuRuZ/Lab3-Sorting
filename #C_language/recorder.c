#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <sys/stat.h>

#ifdef _WIN32
#include <direct.h>
#define MKDIR(path) _mkdir(path)
#else
#include <unistd.h>
#define MKDIR(path) mkdir(path, 0755)
#endif

#include "recorder.h"

const char *CASE_LABELS[NUM_CASES] = {"Random", "Sorted", "ReverseSorted"};
const char *CASE_FILE_NAMES[NUM_CASES] = {
    "Random_Dataset.csv", "Sort_Dataset.csv", "ReverseSort_Dataset.csv"};

/* ---------------- small portable helpers ---------------- */

/* Creates every path segment (splitting on '/' or '\\') so nested dirs work
 * cross-platform, e.g. "csv/100000(n)" on both Windows and Linux. */
static void mkdir_p(const char *path) {
    char buf[1024];
    size_t len = strlen(path);
    if (len >= sizeof(buf)) {
        len = sizeof(buf) - 1;
    }
    memcpy(buf, path, len);
    buf[len] = '\0';

    for (size_t i = 1; i < len; i++) {
        if (buf[i] == '/' || buf[i] == '\\') {
            char c = buf[i];
            buf[i] = '\0';
            MKDIR(buf);
            buf[i] = c;
        }
    }
    MKDIR(buf);
}

static void build_path(char *out, size_t outSize, const char *dir, const char *fileName) {
    snprintf(out, outSize, "%s/%s", dir, fileName);
}

/* Reads one line of arbitrary length from f into a malloc'd buffer (caller frees).
 * Returns NULL at EOF with nothing read. Strips the trailing newline. */
static char *read_line_dynamic(FILE *f) {
    size_t cap = 256;
    size_t len = 0;
    char *buf = (char *)malloc(cap);
    int c;
    int gotAny = 0;

    while ((c = fgetc(f)) != EOF) {
        gotAny = 1;
        if (c == '\n') {
            break;
        }
        if (len + 1 >= cap) {
            cap *= 2;
            buf = (char *)realloc(buf, cap);
        }
        buf[len++] = (char)c;
    }
    if (!gotAny) {
        free(buf);
        return NULL;
    }
    if (len > 0 && buf[len - 1] == '\r') {
        len--;
    }
    buf[len] = '\0';
    return buf;
}

/* Splits a simple (no embedded commas/quotes) csv line into fields.
 * Returns the field count and fills *outFields with malloc'd strings (caller frees each + the array). */
static int split_csv_line(const char *line, char ***outFields) {
    int count = 1;
    for (const char *p = line; *p; p++) {
        if (*p == ',') {
            count++;
        }
    }
    char **fields = (char **)malloc(sizeof(char *) * (size_t)count);
    int idx = 0;
    const char *start = line;
    for (const char *p = line;; p++) {
        if (*p == ',' || *p == '\0') {
            size_t flen = (size_t)(p - start);
            char *field = (char *)malloc(flen + 1);
            memcpy(field, start, flen);
            field[flen] = '\0';
            fields[idx++] = field;
            if (*p == '\0') {
                break;
            }
            start = p + 1;
        }
    }
    *outFields = fields;
    return count;
}

static void free_fields(char **fields, int count) {
    for (int i = 0; i < count; i++) {
        free(fields[i]);
    }
    free(fields);
}

/* ---------------- capacity growth for the times[][] arrays ---------------- */

static void ensure_trial_capacity(DatasetRecorder *rec, int neededTrials) {
    if (neededTrials <= rec->trialCapacity) {
        return;
    }
    int newCap = rec->trialCapacity > 0 ? rec->trialCapacity : 8;
    while (newCap < neededTrials) {
        newCap *= 2;
    }
    for (int a = 0; a < NUM_ALGS; a++) {
        for (int c = 0; c < NUM_CASES; c++) {
            double *grown = (double *)realloc(rec->times[a][c], sizeof(double) * (size_t)newCap);
            for (int t = rec->trialCapacity; t < newCap; t++) {
                grown[t] = 0.0;
            }
            rec->times[a][c] = grown;
        }
    }
    rec->trialCapacity = newCap;
}

static void ensure_random_values_capacity(DatasetRecorder *rec, int neededTrials) {
    if (neededTrials <= rec->randomValuesCapacity) {
        return;
    }
    int newCap = rec->randomValuesCapacity > 0 ? rec->randomValuesCapacity : 8;
    while (newCap < neededTrials) {
        newCap *= 2;
    }
    int **grown = (int **)realloc(rec->randomValues, sizeof(int *) * (size_t)newCap);
    for (int t = rec->randomValuesCapacity; t < newCap; t++) {
        grown[t] = NULL;
    }
    rec->randomValues = grown;
    rec->randomValuesCapacity = newCap;
}

/* ---------------- loading existing data (for APPEND) ---------------- */

/* Parses the header of an already-open csv file and returns how many
 * "Trial N" columns exist before a trailing "Average" column (0 if the file
 * is new/empty). trialColStart is the column index (0-based) of "Trial 1". */
static int count_trial_columns(char **headerFields, int fieldCount, int trialColStart) {
    int last = fieldCount - 1;
    int hasAverage = (last >= trialColStart) &&
                      (strcmp(headerFields[last], "Average") == 0);
    int trialCols = hasAverage ? (last - trialColStart) : (fieldCount - trialColStart);
    return trialCols < 0 ? 0 : trialCols;
}

/* Loads one case file (Random/Sort/ReverseSort_Dataset.csv) into rec->times[*][caseType][*].
 * Rows are trusted to always be written in ALGORITHMS[] order (Bubble..Quick). */
static void load_case_file(DatasetRecorder *rec, CaseType caseType) {
    char path[1200];
    build_path(path, sizeof(path), rec->dir, CASE_FILE_NAMES[caseType]);
    FILE *f = fopen(path, "r");
    if (!f) {
        return; /* file doesn't exist yet - nothing to load */
    }

    char *headerLine = read_line_dynamic(f);
    if (!headerLine) {
        fclose(f);
        return;
    }
    char **headerFields;
    int headerCount = split_csv_line(headerLine, &headerFields);
    int trialCols = count_trial_columns(headerFields, headerCount, 2);
    free_fields(headerFields, headerCount);
    free(headerLine);

    if (trialCols > rec->numTrials) {
        rec->numTrials = trialCols;
    }
    ensure_trial_capacity(rec, rec->numTrials);

    for (int a = 0; a < NUM_ALGS; a++) {
        char *line = read_line_dynamic(f);
        if (!line) {
            break;
        }
        char **fields;
        int fieldCount = split_csv_line(line, &fields);
        for (int t = 0; t < trialCols && (2 + t) < fieldCount; t++) {
            rec->times[a][caseType][t] = atof(fields[2 + t]);
        }
        free_fields(fields, fieldCount);
        free(line);
    }
    fclose(f);
}

static void load_random_values_file(DatasetRecorder *rec) {
    if (!rec->saveRandomValues) {
        return;
    }
    char path[1200];
    build_path(path, sizeof(path), rec->dir, "RandomValues_Dataset.csv");
    FILE *f = fopen(path, "r");
    if (!f) {
        return;
    }

    char *headerLine = read_line_dynamic(f);
    if (!headerLine) {
        fclose(f);
        return;
    }
    char **headerFields;
    int headerCount = split_csv_line(headerLine, &headerFields);
    int trialCols = headerCount - 1; /* col 0 = "Random Value Index", no trailing Average here */
    free_fields(headerFields, headerCount);
    free(headerLine);
    if (trialCols < 0) {
        trialCols = 0;
    }

    rec->randomValuesTrials = trialCols;
    ensure_random_values_capacity(rec, trialCols);
    for (int t = 0; t < trialCols; t++) {
        rec->randomValues[t] = (int *)malloc(sizeof(int) * (size_t)rec->inputSize);
    }

    for (int i = 0; i < rec->inputSize; i++) {
        char *line = read_line_dynamic(f);
        if (!line) {
            break;
        }
        char **fields;
        int fieldCount = split_csv_line(line, &fields);
        for (int t = 0; t < trialCols && (1 + t) < fieldCount; t++) {
            rec->randomValues[t][i] = atoi(fields[1 + t]);
        }
        free_fields(fields, fieldCount);
        free(line);
    }
    fclose(f);
}

/* ---------------- public API ---------------- */

DatasetRecorder *recorder_open(const char *dir, int inputSize, int saveRandomValues) {
    DatasetRecorder *rec = (DatasetRecorder *)calloc(1, sizeof(DatasetRecorder));
    snprintf(rec->dir, sizeof(rec->dir), "%s", dir);
    rec->inputSize = inputSize;
    rec->saveRandomValues = saveRandomValues;
    mkdir_p(rec->dir);

    load_case_file(rec, CASE_RANDOM);
    load_case_file(rec, CASE_SORTED);
    load_case_file(rec, CASE_REVERSE_SORTED);
    load_random_values_file(rec);

    if (rec->saveRandomValues && rec->randomValuesTrials > rec->numTrials) {
        rec->numTrials = rec->randomValuesTrials;
    }

    return rec;
}

int recorder_get_next_trial(const DatasetRecorder *rec) {
    return rec->numTrials + 1;
}

void recorder_record_timing(DatasetRecorder *rec, CaseType caseType, int algIndex, int trial, double nanos) {
    ensure_trial_capacity(rec, trial);
    rec->times[algIndex][caseType][trial - 1] = nanos;
    if (trial > rec->numTrials) {
        rec->numTrials = trial;
    }
}

void recorder_record_random_array(DatasetRecorder *rec, const int *values, int n, int trial) {
    if (!rec->saveRandomValues) {
        return;
    }
    ensure_random_values_capacity(rec, trial);
    int idx = trial - 1;
    if (rec->randomValues[idx] == NULL) {
        rec->randomValues[idx] = (int *)malloc(sizeof(int) * (size_t)rec->inputSize);
    }
    memcpy(rec->randomValues[idx], values, sizeof(int) * (size_t)n);
    if (trial > rec->randomValuesTrials) {
        rec->randomValuesTrials = trial;
    }
    if (trial > rec->numTrials) {
        rec->numTrials = trial;
    }
}

/* Writes one case file and returns each algorithm's average via outAverages[NUM_ALGS]. */
static void save_case_file(DatasetRecorder *rec, CaseType caseType, double *outAverages) {
    char path[1200];
    build_path(path, sizeof(path), rec->dir, CASE_FILE_NAMES[caseType]);
    FILE *f = fopen(path, "w");
    if (!f) {
        return;
    }

    fprintf(f, "Algorithm ,Input Size(n)");
    for (int t = 1; t <= rec->numTrials; t++) {
        fprintf(f, ",Trial %d", t);
    }
    fprintf(f, ",Average\n");

    for (int a = 0; a < NUM_ALGS; a++) {
        fprintf(f, "%s,%d", ALGORITHMS[a].name, rec->inputSize);
        double sum = 0.0;
        int count = 0;
        for (int t = 0; t < rec->numTrials; t++) {
            double v = rec->times[a][caseType][t];
            fprintf(f, TIME_UNIT == TIME_UNIT_MS ? ",%.4f" : ",%.1f", v);
            sum += v;
            count++;
        }
        double avg = count > 0 ? sum / count : 0.0;
        fprintf(f, TIME_UNIT == TIME_UNIT_MS ? ",%.4f\n" : ",%.1f\n", avg);
        outAverages[a] = avg;
    }
    fclose(f);
}

static void save_raw_file(DatasetRecorder *rec, double averages[NUM_CASES][NUM_ALGS]) {
    char path[1200];
    build_path(path, sizeof(path), rec->dir, "RAW_Dataset.csv");
    FILE *f = fopen(path, "w");
    if (!f) {
        return;
    }

    fprintf(f, "Algorithm ,Case,Input Size(n)");
    for (int t = 1; t <= rec->numTrials; t++) {
        fprintf(f, ",Trial %d", t);
    }
    fprintf(f, ",Average\n");

    for (int a = 0; a < NUM_ALGS; a++) {
        for (int c = 0; c < NUM_CASES; c++) {
            fprintf(f, "%s,%s,%d", ALGORITHMS[a].name, CASE_LABELS[c], rec->inputSize);
            for (int t = 0; t < rec->numTrials; t++) {
                fprintf(f, TIME_UNIT == TIME_UNIT_MS ? ",%.4f" : ",%.1f", rec->times[a][c][t]);
            }
            fprintf(f, TIME_UNIT == TIME_UNIT_MS ? ",%.4f\n" : ",%.1f\n", averages[c][a]);
        }
    }
    fclose(f);
}

static void save_avg_file(DatasetRecorder *rec, const char *fileName, const double *averages) {
    char path[1200];
    build_path(path, sizeof(path), rec->dir, fileName);
    FILE *f = fopen(path, "w");
    if (!f) {
        return;
    }
    fprintf(f, "InputSize(n)");
    for (int a = 0; a < NUM_ALGS; a++) {
        fprintf(f, ",%s", ALGORITHMS[a].avgHeader);
    }
    fprintf(f, "\n%d", rec->inputSize);
    for (int a = 0; a < NUM_ALGS; a++) {
        fprintf(f, TIME_UNIT == TIME_UNIT_MS ? ",%.4f" : ",%.1f", averages[a]);
    }
    fprintf(f, "\n");
    fclose(f);
}

static void save_random_values_file(DatasetRecorder *rec) {
    if (!rec->saveRandomValues) {
        return;
    }
    char path[1200];
    build_path(path, sizeof(path), rec->dir, "RandomValues_Dataset.csv");
    FILE *f = fopen(path, "w");
    if (!f) {
        return;
    }
    fprintf(f, "Random Value Index");
    for (int t = 1; t <= rec->numTrials; t++) {
        fprintf(f, ",Trial %d", t);
    }
    fprintf(f, "\n");

    for (int i = 0; i < rec->inputSize; i++) {
        fprintf(f, "%d", i + 1);
        for (int t = 0; t < rec->numTrials; t++) {
            int value = (rec->randomValues != NULL && t < rec->randomValuesCapacity &&
                         rec->randomValues[t] != NULL)
                            ? rec->randomValues[t][i]
                            : 0;
            fprintf(f, ",%d", value);
        }
        fprintf(f, "\n");
    }
    fclose(f);
}

void recorder_save_all(DatasetRecorder *rec) {
    double averages[NUM_CASES][NUM_ALGS];

    save_case_file(rec, CASE_RANDOM, averages[CASE_RANDOM]);
    save_case_file(rec, CASE_SORTED, averages[CASE_SORTED]);
    save_case_file(rec, CASE_REVERSE_SORTED, averages[CASE_REVERSE_SORTED]);

    save_raw_file(rec, averages);

    save_avg_file(rec, "AVG_Random_Dataset.csv", averages[CASE_RANDOM]);
    save_avg_file(rec, "AVG_Sort_Dataset.csv", averages[CASE_SORTED]);
    save_avg_file(rec, "AVG_ReverseSort_Dataset.csv", averages[CASE_REVERSE_SORTED]);

    save_random_values_file(rec);
}

void recorder_close(DatasetRecorder *rec) {
    if (!rec) {
        return;
    }
    for (int a = 0; a < NUM_ALGS; a++) {
        for (int c = 0; c < NUM_CASES; c++) {
            free(rec->times[a][c]);
        }
    }
    if (rec->randomValues) {
        for (int t = 0; t < rec->randomValuesCapacity; t++) {
            free(rec->randomValues[t]);
        }
        free(rec->randomValues);
    }
    free(rec);
}
