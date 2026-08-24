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

        System.out.println("Sort");

        timeSort("Bubble Sort", new BubbleSort(), arr.clone(), false);
        timeSort("Insertion Sort", new InsertionSort(), arr.clone(), false);
        timeSort("Selection Sort", new SelectionSort(), arr.clone(), false);
        timeSort("Merge Sort", new MergeSort(), arr.clone(), false);
        timeSort("Quick Sort", new QuickSort(), arr.clone(), false);

        System.out.println("Reverse Sort"); 
        
        timeSort("Bubble Sort (Reverse)", new BubbleSort(), arr.clone(), true);
        timeSort("Insertion Sort (Reverse)", new InsertionSort(), arr.clone(), true);
        timeSort("Selection Sort (Reverse)", new SelectionSort(), arr.clone(), true);
        timeSort("Merge Sort (Reverse)", new MergeSort(), arr.clone(), true);
        timeSort("Quick Sort (Reverse)", new QuickSort(), arr.clone(), true);
    }

    private static int[] generateRandomArray(int size, int bound) {
        Random rand = new Random();
        int[] arr = new int[size];
        for (int i = 0; i < size; i++) {
            arr[i] = rand.nextInt(bound);
        }
        return arr;
    }

    private static void timeSort(String label, Sorter sorter, int[] arr, boolean reverse) {
        long startTime = System.nanoTime();
        if (reverse) {
            sorter.reverseSort(arr);
        } else {
            sorter.sort(arr);
        }
        long endTime = System.nanoTime();
        System.out.println(label + " Time: " + (endTime - startTime) + " nanoseconds");
    }

}