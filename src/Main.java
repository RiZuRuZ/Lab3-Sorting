import alg.BubbleSort;
import alg.InsertionSort;
import alg.MergeSort;
import alg.QuickSort;
import alg.SelectionSort;
import alg.Sorter;
import java.util.Random;

public class Main {

    public static void main(String[] args) {
        int[] arr = generateRandomArray(10000, 10000);

        timeSort("Bubble Sort", new BubbleSort(), arr.clone());
        timeSort("Insertion Sort", new InsertionSort(), arr.clone());
        timeSort("Selection Sort", new SelectionSort(), arr.clone());
        timeSort("Merge Sort", new MergeSort(), arr.clone());
        timeSort("Quick Sort", new QuickSort(), arr.clone());
    }

    private static int[] generateRandomArray(int size, int bound) {
        Random rand = new Random();
        int[] arr = new int[size];
        for (int i = 0; i < size; i++) {
            arr[i] = rand.nextInt(bound);
        }
        return arr;
    }

    private static void timeSort(String label, Sorter sorter, int[] arr) {
        long startTime = System.nanoTime();
        sorter.sort(arr);
        long endTime = System.nanoTime();
        System.out.println(label + " Time: " + (endTime - startTime) + " nanoseconds");
    }

}