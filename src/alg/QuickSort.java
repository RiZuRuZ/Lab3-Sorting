package alg;

public class QuickSort implements Sorter {
    public int[] sort(int[] arr){
        if(arr.length <= 1){
            return arr;
        }
        int pivot = arr[arr.length / 2];
        int[] left = new int[arr.length];
        int[] right = new int[arr.length];
        int leftIndex = 0;
        int rightIndex = 0;
        for(int i=0; i<arr.length; i++){
            if(arr[i] < pivot){
                left[leftIndex++] = arr[i];
            }else if(arr[i] > pivot){
                right[rightIndex++] = arr[i];
            }
        }
        left = sort(copyOf(left, leftIndex));
        right = sort(copyOf(right, rightIndex));
        return merge(left, right, pivot);
    }

    public int[] merge(int[] left, int[] right, int pivot){
        int[] result = new int[left.length + right.length + 1];
        for(int i=0; i<left.length; i++){
            result[i] = left[i];
        }
        result[left.length] = pivot;
        for(int i=0; i<right.length; i++){
            result[left.length + 1 + i] = right[i];
        }
        return result;
    }

    public int[] reverseSort(int[] arr){
        if(arr.length <= 1){
            return arr;
        }
        int pivot = arr[arr.length / 2];
        int[] left = new int[arr.length];
        int[] right = new int[arr.length];
        int leftIndex = 0;
        int rightIndex = 0;
        for(int i=0; i<arr.length; i++){
            if(arr[i] > pivot){
                left[leftIndex++] = arr[i];
            }else if(arr[i] < pivot){
                right[rightIndex++] = arr[i];
            }
        }
        left = reverseSort(copyOf(left, leftIndex));
        right = reverseSort(copyOf(right, rightIndex));
        return mergeReverse(left, right, pivot);
    }

    public int[] mergeReverse(int[] left, int[] right, int pivot){
        int[] result = new int[left.length + right.length + 1];
        for(int i=0; i<left.length; i++){
            result[i] = left[i];
        }
        result[left.length] = pivot;
        for(int i=0; i<right.length; i++){
            result[left.length + 1 + i] = right[i];
        }
        return result;
    }

    private int[] copyOf(int[] source, int length){
        int[] copy = new int[length];
        for(int i = 0; i < length; i++){
            copy[i] = source[i];
        }
        return copy;
    }
}
