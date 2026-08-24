import java.util.Random;

public class Main {
    public static void main(String[] args) {
        int[] arr = new int[10000]; // Create an array of size 10000
        Random rand = new Random();

        for (int i = 0; i < arr.length; i++) {
            arr[i] = rand.nextInt(10000); // Generates random values between 0 and 9999
        }

        //int[] arr = {56, 474, 234, 6, 8, 0, 47, 67, 6767};
        int[] arr1 = new BubbleSort().sort(arr);
        int[] arr2 = new InsertionSort().sort(arr);
        int[] arr3 = new SelectionSort().sort(arr);
        //System.out.println(java.util.Arrays.toString(arr1));
        //System.out.println(java.util.Arrays.toString(arr2));
        //System.out.println(java.util.Arrays.toString(arr3));
        //show time that used to sort the array
        long startTime = System.nanoTime();
        new BubbleSort().sort(arr);
        long endTime = System.nanoTime();
        long duration = (endTime - startTime);
        System.out.println("Bubble Sort Time: " + duration + " nanoseconds");

        startTime = System.nanoTime();
        new InsertionSort().sort(arr);
        endTime = System.nanoTime();
        duration = (endTime - startTime);
        System.out.println("Insertion Sort Time: " + duration + " nanoseconds");

        startTime = System.nanoTime();
        new SelectionSort().sort(arr);
        endTime = System.nanoTime();
        duration = (endTime - startTime);
        System.out.println("Selection Sort Time: " + duration + " nanoseconds");
    }
    
}
