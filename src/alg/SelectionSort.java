package alg;
public class SelectionSort implements Sorter {
    public int[] sort(int[] arr){
        int n = arr.length;
        for(int i=0; i<n-1; i++){
            int minIndex = i;
            for(int j=i+1; j<n; j++){
                if(arr[j] < arr[minIndex]){
                    minIndex = j;
                }
            }
            // swap arr[i] and arr[minIndex]
            int temp = arr[i];
            arr[i] = arr[minIndex];
            arr[minIndex] = temp;
        }
        return arr;
    }

    public int[] reverseSort(int[] arr){
        int n = arr.length;
        for(int i=0; i<n-1; i++){
            int maxIndex = i;
            for(int j=i+1; j<n; j++){
                if(arr[j] > arr[maxIndex]){
                    maxIndex = j;
                }
            }
            // swap arr[i] and arr[maxIndex]
            int temp = arr[i];
            arr[i] = arr[maxIndex];
            arr[maxIndex] = temp;
        }
        return arr;
    }
}
