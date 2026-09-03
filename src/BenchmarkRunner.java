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
 * Runs the sorting benchmark for 3 input patterns and saves everything into the
 * excel/ files via DatasetRecorder.
 *
 * For each input size, for each trial, and for every algorithm:
 *   1. RANDOM         - ascending-sort a freshly generated random array (average case)
 *   2. SORTED         - ascending-sort an array that is ALREADY ascending (best case)
 *   3. REVERSE_SORTED - descending-sort (reverseSort) an array that is ALREADY
 *                       ascending, i.e. turn 1..n into n..1 (worst case)
 *
 * Random Seed: seed = BASE_SEED + size*1000 + trial, so the same (size, trial)
 * pair always generates the exact same random array, regardless of when or how
 * many times the program has been run (needed for APPEND to work correctly and
 * for the experiment to be reproducible, per Lab 2's "Controlled Variables").
 */
public class BenchmarkRunner {

    // Manual switch:
    //   true  -> append new trials after whatever is already in the files
    //   false -> old behavior, always start from Trial 1 (overwrites existing trials)
    private static final boolean APPEND = false;

    // How many trials to run this time (new trials if APPEND=true, total trials if APPEND=false).
    private static final int NUM_NEW_TRIALS = 20;

    // This run writes to the workbook set for n-element arrays.
    private static final int CURRENT_N = 1000;

    private static final int[] INPUT_SIZES = { CURRENT_N };

    private static final String EXCEL_DIR = "excel\\" + CURRENT_N + "(n)";

    // Fixed base seed so every (size, trial) pair always produces the same random array.
    private static final long BASE_SEED = 42;

    // How many extra untimed sort() + reverseSort() calls to run per algorithm,
    // BEFORE Trial 1, so the JVM JIT-compiles hot methods and Trial 1 is measured
    // at the same "warmed up" state as every later trial (removes JIT warm-up bias).
    private static final int WARMUP_ITERATIONS = 5;

    public static void main(String[] args) throws IOException {
        Map<String, Sorter> algorithms = new LinkedHashMap<>();
        algorithms.put("Bubble Sort", new BubbleSort());
        algorithms.put("Insertion Sort", new InsertionSort());
        algorithms.put("Selection Sort", new SelectionSort());
        algorithms.put("Merge Sort", new MergeSort());
        algorithms.put("Quick Sort", new QuickSort());

        try (DatasetRecorder recorder = new DatasetRecorder(EXCEL_DIR)) {
            // APPEND=true  -> continue after the last existing "Trial N" column
            // APPEND=false -> start over from Trial 1 (old behavior)
            int startTrial = APPEND ? recorder.getNextTrialNumber() : 1;
            int endTrial = startTrial + NUM_NEW_TRIALS - 1;
            System.out.println((APPEND ? "Appending" : "Overwriting from start")
                    + " - Trial " + startTrial + " through Trial " + endTrial);

            for (int size : INPUT_SIZES) {
                warmUp(algorithms, size);

                for (int trial = startTrial; trial <= endTrial; trial++) {
                    int[] original = generateRandomArray(size, trial);
                    int[] ascendingInput = sortedCopyAscending(original);

                    for (Map.Entry<String, Sorter> entry : algorithms.entrySet()) {
                        String name = entry.getKey();
                        Sorter sorter = entry.getValue();

                        // RANDOM case: ascending-sort a fresh copy of the random array
                        long randomNanos = timeSort(sorter, original.clone(), false);
                        recorder.recordTiming(DatasetRecorder.Case.RANDOM, name, size, trial, randomNanos);

                        // SORTED case: ascending-sort an array that's already ascending (best case)
                        long sortedNanos = timeSort(sorter, ascendingInput.clone(), false);
                        recorder.recordTiming(DatasetRecorder.Case.SORTED, name, size, trial, sortedNanos);

                        // REVERSE_SORTED case: descending-sort an array that's already ascending (worst case)
                        long reverseNanos = timeSort(sorter, ascendingInput.clone(), true);
                        recorder.recordTiming(DatasetRecorder.Case.REVERSE_SORTED, name, size, trial, reverseNanos);
                    }

                    System.out.println("Size " + size + ", trial " + trial + "/" + endTrial + " done.");
                }
            }

            recorder.computeAverages();
            recorder.saveAll();
        }

        System.out.println("All excel files updated in " + EXCEL_DIR + "/");
    }

    /**
     * Runs every algorithm WARMUP_ITERATIONS times (sort + reverseSort) on throwaway
     * arrays, without recording anything, so the JVM interpreter -> JIT-compiled
     * transition happens BEFORE Trial 1 is timed. Uses negative "trial" numbers in
     * the seed so warm-up arrays never collide with the real trial arrays (1..N).
     */
    private static void warmUp(Map<String, Sorter> algorithms, int size) {
        System.out.println("Warming up JVM for size " + size + " (" + WARMUP_ITERATIONS
                + " untimed iterations per algorithm)...");
        for (Map.Entry<String, Sorter> entry : algorithms.entrySet()) {
            Sorter sorter = entry.getValue();
            for (int i = 1; i <= WARMUP_ITERATIONS; i++) {
                int[] warmupOriginal = generateRandomArray(size, -i);
                int[] warmupAscending = sortedCopyAscending(warmupOriginal);
                sorter.sort(warmupOriginal.clone());
                sorter.sort(warmupAscending.clone());
                sorter.reverseSort(warmupAscending.clone());
            }
        }
        System.out.println("Warm-up complete.");
    }

    /** seed = BASE_SEED + size*1000 + trial -> same (size, trial) always gives the same array. */
    private static int[] generateRandomArray(int size, int trial) {
        long seed = BASE_SEED + (long) size * 1000L + trial;
        Random rand = new Random(seed);
        int[] arr = new int[size];
        for (int i = 0; i < size; i++) {
            arr[i] = rand.nextInt(size);
        }
        return arr;
    }

    private static int[] sortedCopyAscending(int[] arr) {
        int[] copy = arr.clone();
        Arrays.sort(copy);
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