import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import alg.BubbleSort;
import alg.InsertionSort;
import alg.MergeSort;
import alg.QuickSort;
import alg.SelectionSort;
import alg.Sorter;

/**
 * Runs the sorting benchmark and saves everything into the excel/ files
 * via DatasetRecorder.
 *
 * For each input size, for each trial:
 *   1. generate a random array               -> Random_Dataset.xlsx
 *   2. its ascending-sorted version           -> Sort_Dataset.xlsx
 *   3. its descending-sorted version          -> ReverseSort_Dataset.xlsx
 *   4. for every algorithm, time sort() and reverseSort() on clones
 *      of the ORIGINAL random array           -> RAW_Dataset.xlsx
 * Then AVG_Sort_Dataset.xlsx is (re)computed as the per-algorithm,
 * per-size average of RAW_Dataset.xlsx.
 */
public class BenchmarkRunner {

    // Matches the 50 "Trial N" columns already in Random/Sort/ReverseSort_Dataset.xlsx
    private static final int TRIALS = 50;

    // This run writes to the workbook set for 100-element arrays.
    private static final int[] INPUT_SIZES = { 100 };

    private static final String EXCEL_DIR = "excel\\100(n)";

    public static void main(String[] args) throws IOException {
        Map<String, Sorter> algorithms = new LinkedHashMap<>();
        algorithms.put("Bubble Sort", new BubbleSort());
        algorithms.put("Insertion Sort", new InsertionSort());
        algorithms.put("Selection Sort", new SelectionSort());
        algorithms.put("Merge Sort", new MergeSort());
        algorithms.put("Quick Sort", new QuickSort());

        try (DatasetRecorder recorder = new DatasetRecorder(EXCEL_DIR)) {
            for (int size : INPUT_SIZES) {
                for (int trial = 1; trial <= TRIALS; trial++) {
                    int[] original = generateRandomArray(size, size);

                    recorder.recordRandomArray(trial, original);
                    recorder.recordSortedArray(trial, sortedCopyAscending(original));
                    recorder.recordReverseSortedArray(trial, sortedCopyDescending(original));

                    for (Map.Entry<String, Sorter> entry : algorithms.entrySet()) {
                        String name = entry.getKey();
                        Sorter sorter = entry.getValue();

                        long ascNanos = timeSort(sorter, original.clone(), false);
                        recorder.recordRawTiming(name, size, trial, ascNanos);

                        long descNanos = timeSort(sorter, original.clone(), true);
                        recorder.recordRawTiming(name + " (Reverse)", size, trial, descNanos);
                    }

                    System.out.println("Size " + size + ", trial " + trial + "/" + TRIALS + " done.");
                }
            }

            recorder.computeAverages();
            recorder.saveAll();
        }

        System.out.println("All 5 excel files updated in " + EXCEL_DIR + "/");
    }

    private static int[] generateRandomArray(int size, int bound) {
        Random rand = new Random();
        int[] arr = new int[size];
        for (int i = 0; i < size; i++) {
            arr[i] = rand.nextInt(bound);
        }
        return arr;
    }

    private static int[] sortedCopyAscending(int[] arr) {
        int[] copy = arr.clone();
        Arrays.sort(copy);
        return copy;
    }

    private static int[] sortedCopyDescending(int[] arr) {
        int[] copy = sortedCopyAscending(arr);
        for (int i = 0; i < copy.length / 2; i++) {
            int tmp = copy[i];
            copy[i] = copy[copy.length - 1 - i];
            copy[copy.length - 1 - i] = tmp;
        }
        return copy;
    }

    private static long timeSort(Sorter sorter, int[] arr, boolean reverse) {
        long start = System.nanoTime();
        if (reverse) {
            sorter.reverseSort(arr);
        } else {
            sorter.sort(arr);
        }
        return System.nanoTime() - start;
    }
}
