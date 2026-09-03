import java.util.Arrays;
import java.util.Random;

public class RandomArrayTest {

    // กำหนด Base Seed ค่าเดิมจากโค้ดของคุณ
    private static final long BASE_SEED = 42;

    public static void main(String[] args) {
        // กำหนดค่าทดสอบ (สมมติให้ size = 15 และทดสอบ trial ที่ 1 และ 2)
        int size = 15; 
        int i = 1;
        
        while(i<10){
            System.out.println("\n--- Test Trial " + i + " ---");
            int[] arr = generateRandomArray(size, i);
            System.out.println(Arrays.toString(arr));
            i++;
        }
    }

    /** 
     * ฟังก์ชันสุ่มตัวเลขลง Array 
     * seed = BASE_SEED + size*1000 + trial -> ค่า (size, trial) เดิม จะได้ Array หน้าตาเหมือนเดิมเสมอ
     */
    private static int[] generateRandomArray(int size, int trial) {
        long seed = BASE_SEED + (long) size * 1000L + trial;
        Random rand = new Random(seed);
        
        int[] arr = new int[size];
        for (int i = 0; i < size; i++) {
            arr[i] = rand.nextInt(size);
        }
        
        return arr;
    }
}