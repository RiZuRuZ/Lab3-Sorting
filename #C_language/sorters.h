#ifndef SORTERS_H
#define SORTERS_H

#define NUM_ALGS 5

/*
 * Every sort function takes a pointer-to-pointer so that algorithms which
 * allocate brand new arrays (Merge Sort, Quick Sort - ported as-is from the
 * Java version, which is NOT in-place) can swap *arrPtr to point at the new
 * result and free the old one internally. In-place algorithms (Bubble,
 * Insertion, Selection) just sort the contents and leave *arrPtr unchanged.
 * Either way, the caller always ends up with *arrPtr pointing at the sorted
 * (or reverse-sorted) result, and is responsible for free()-ing it.
 */
typedef void (*SortFunc)(int **arrPtr, int n);

typedef struct {
    const char *name;        /* e.g. "Bubble Sort"  - used in RAW/case files      */
    const char *avgHeader;   /* e.g. "BubbleSort"   - used as a column in AVG files*/
    SortFunc ascending;
    SortFunc descending;
} Algorithm;

extern const Algorithm ALGORITHMS[NUM_ALGS];

void bubble_sort_asc(int **arrPtr, int n);
void bubble_sort_desc(int **arrPtr, int n);

void insertion_sort_asc(int **arrPtr, int n);
void insertion_sort_desc(int **arrPtr, int n);

void selection_sort_asc(int **arrPtr, int n);
void selection_sort_desc(int **arrPtr, int n);

void merge_sort_asc(int **arrPtr, int n);
void merge_sort_desc(int **arrPtr, int n);

void quick_sort_asc(int **arrPtr, int n);
void quick_sort_desc(int **arrPtr, int n);

#endif
