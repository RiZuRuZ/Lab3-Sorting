#include <stdlib.h>
#include <string.h>
#include "sorters.h"

/* ---------------- Bubble Sort (in-place, ported from BubbleSort.java) ---------------- */

static void bubble_sort_core(int *arr, int n, int ascending) {
    for (int i = 0; i < n - 1; i++) {
        for (int j = 0; j < n - i - 1; j++) {
            int shouldSwap = ascending ? (arr[j] > arr[j + 1]) : (arr[j] < arr[j + 1]);
            if (shouldSwap) {
                int tmp = arr[j];
                arr[j] = arr[j + 1];
                arr[j + 1] = tmp;
            }
        }
    }
}

void bubble_sort_asc(int **arrPtr, int n) { bubble_sort_core(*arrPtr, n, 1); }
void bubble_sort_desc(int **arrPtr, int n) { bubble_sort_core(*arrPtr, n, 0); }

/* ---------------- Insertion Sort (in-place, ported from InsertionSort.java) ---------------- */

static void insertion_sort_core(int *arr, int n, int ascending) {
    for (int i = 1; i < n; i++) {
        int key = arr[i];
        int j = i - 1;
        while (j >= 0 && (ascending ? arr[j] > key : arr[j] < key)) {
            arr[j + 1] = arr[j];
            j--;
        }
        arr[j + 1] = key;
    }
}

void insertion_sort_asc(int **arrPtr, int n) { insertion_sort_core(*arrPtr, n, 1); }
void insertion_sort_desc(int **arrPtr, int n) { insertion_sort_core(*arrPtr, n, 0); }

/* ---------------- Selection Sort (in-place, ported from SelectionSort.java) ---------------- */

static void selection_sort_core(int *arr, int n, int ascending) {
    for (int i = 0; i < n - 1; i++) {
        int pick = i;
        for (int j = i + 1; j < n; j++) {
            int better = ascending ? (arr[j] < arr[pick]) : (arr[j] > arr[pick]);
            if (better) {
                pick = j;
            }
        }
        int tmp = arr[i];
        arr[i] = arr[pick];
        arr[pick] = tmp;
    }
}

void selection_sort_asc(int **arrPtr, int n) { selection_sort_core(*arrPtr, n, 1); }
void selection_sort_desc(int **arrPtr, int n) { selection_sort_core(*arrPtr, n, 0); }

/* ---------------- Merge Sort (NOT in-place - allocates new arrays, like MergeSort.java) ---------------- */

static int *merge_arrays(const int *left, int leftLen, const int *right, int rightLen, int ascending) {
    int *result = (int *)malloc(sizeof(int) * (size_t)(leftLen + rightLen));
    int i = 0, j = 0, k = 0;
    while (i < leftLen && j < rightLen) {
        int takeLeft = ascending ? (left[i] <= right[j]) : (left[i] >= right[j]);
        if (takeLeft) {
            result[k++] = left[i++];
        } else {
            result[k++] = right[j++];
        }
    }
    while (i < leftLen) {
        result[k++] = left[i++];
    }
    while (j < rightLen) {
        result[k++] = right[j++];
    }
    return result;
}

static int *merge_sort_recursive(const int *arr, int n, int ascending) {
    if (n <= 1) {
        int *copy = (int *)malloc(sizeof(int) * (size_t)(n > 0 ? n : 1));
        if (n > 0) {
            memcpy(copy, arr, sizeof(int) * (size_t)n);
        }
        return copy;
    }
    int mid = n / 2;
    int *leftSorted = merge_sort_recursive(arr, mid, ascending);
    int *rightSorted = merge_sort_recursive(arr + mid, n - mid, ascending);
    int *merged = merge_arrays(leftSorted, mid, rightSorted, n - mid, ascending);
    free(leftSorted);
    free(rightSorted);
    return merged;
}

static void merge_sort_wrapper(int **arrPtr, int n, int ascending) {
    int *sorted = merge_sort_recursive(*arrPtr, n, ascending);
    free(*arrPtr);
    *arrPtr = sorted;
}

void merge_sort_asc(int **arrPtr, int n) { merge_sort_wrapper(arrPtr, n, 1); }
void merge_sort_desc(int **arrPtr, int n) { merge_sort_wrapper(arrPtr, n, 0); }

/* ---------------- Quick Sort (NOT in-place - 3-way partition into new arrays, like QuickSort.java) ---------------- */

static int *quick_sort_recursive(const int *arr, int n, int ascending) {
    if (n <= 1) {
        int *copy = (int *)malloc(sizeof(int) * (size_t)(n > 0 ? n : 1));
        if (n > 0) {
            memcpy(copy, arr, sizeof(int) * (size_t)n);
        }
        return copy;
    }

    int pivot = arr[n / 2];
    int *left = (int *)malloc(sizeof(int) * (size_t)n);
    int *right = (int *)malloc(sizeof(int) * (size_t)n);
    int leftIdx = 0, rightIdx = 0;

    for (int i = 0; i < n; i++) {
        if (ascending) {
            if (arr[i] < pivot) {
                left[leftIdx++] = arr[i];
            } else if (arr[i] > pivot) {
                right[rightIdx++] = arr[i];
            }
        } else {
            if (arr[i] > pivot) {
                left[leftIdx++] = arr[i];
            } else if (arr[i] < pivot) {
                right[rightIdx++] = arr[i];
            }
        }
    }

    int *leftSorted = quick_sort_recursive(left, leftIdx, ascending);
    int *rightSorted = quick_sort_recursive(right, rightIdx, ascending);
    free(left);
    free(right);

    int *result = (int *)malloc(sizeof(int) * (size_t)n);
    memcpy(result, leftSorted, sizeof(int) * (size_t)leftIdx);
    result[leftIdx] = pivot;
    memcpy(result + leftIdx + 1, rightSorted, sizeof(int) * (size_t)rightIdx);
    free(leftSorted);
    free(rightSorted);
    return result;
}

static void quick_sort_wrapper(int **arrPtr, int n, int ascending) {
    int *sorted = quick_sort_recursive(*arrPtr, n, ascending);
    free(*arrPtr);
    *arrPtr = sorted;
}

void quick_sort_asc(int **arrPtr, int n) { quick_sort_wrapper(arrPtr, n, 1); }
void quick_sort_desc(int **arrPtr, int n) { quick_sort_wrapper(arrPtr, n, 0); }

/* ---------------- Algorithm table ---------------- */

const Algorithm ALGORITHMS[NUM_ALGS] = {
    {"Bubble Sort", "BubbleSort", bubble_sort_asc, bubble_sort_desc},
    {"Insertion Sort", "InsertionSort", insertion_sort_asc, insertion_sort_desc},
    {"Selection Sort", "SelectionSort", selection_sort_asc, selection_sort_desc},
    {"Merge Sort", "MergeSort", merge_sort_asc, merge_sort_desc},
    {"Quick Sort", "QuickSort", quick_sort_asc, quick_sort_desc},
};
