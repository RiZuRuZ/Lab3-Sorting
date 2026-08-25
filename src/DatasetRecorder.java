import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Loads the 5 lab excel files, lets you record trial data into them in
 * memory, then writes everything back out once with saveAll().
 *
 * Files expected under excelDir (headers already present in each):
 *   RAW_Dataset.xlsx        -> Algorithm | Input Size(n) | Trial 1..N
 *   AVG_Sort_Dataset.xlsx   -> InputSize(n) | BubbleSort | InsertionSort | SelectionSort | MergeSort | QuickSort
 *   Random_Dataset.xlsx     -> Trial 1..N   (each column = the raw generated array for that trial)
 *   Sort_Dataset.xlsx       -> Trial 1..N   (each column = that trial's array sorted ascending)
 *   ReverseSort_Dataset.xlsx-> Trial 1..N   (each column = that trial's array sorted descending)
 */
public class DatasetRecorder implements Closeable {

    private enum ArrayDataset { RANDOM, SORT, REVERSE_SORT }

    private static final String RAW_FILE = "RAW_Dataset.xlsx";
    private static final String AVG_FILE = "AVG_Sort_Dataset.xlsx";
    private static final String RANDOM_FILE = "Random_Dataset.xlsx";
    private static final String SORT_FILE = "Sort_Dataset.xlsx";
    private static final String REVERSE_SORT_FILE = "ReverseSort_Dataset.xlsx";

    // Column order/labels used in AVG_Sort_Dataset.xlsx (must match the algorithm's
    // base name, i.e. without a trailing " (Reverse)").
    private static final String[] AVG_ALGORITHM_HEADERS = {
            "BubbleSort ", "InsertionSort", "SelectionSort", "MergeSort", "QuickSort"
    };
    private static final String[] AVG_ALGORITHM_KEYS = {
            "Bubble Sort", "Insertion Sort", "Selection Sort", "Merge Sort", "Quick Sort"
    };

    private final File excelDir;

    private final Workbook rawWb;
    private final Sheet rawSheet;

    private final Workbook avgWb;
    private final Sheet avgSheet;

    private final Workbook randomWb;
    private final Sheet randomSheet;

    private final Workbook sortWb;
    private final Sheet sortSheet;

    private final Workbook reverseSortWb;
    private final Sheet reverseSortSheet;

    public DatasetRecorder(String excelDirPath) throws IOException {
        this.excelDir = new File(excelDirPath);
        if (!excelDir.exists()) {
            excelDir.mkdirs();
        }

        rawWb = openOrCreate(RAW_FILE);
        rawSheet = firstSheet(rawWb);
        ensureRawHeader(rawSheet);

        avgWb = openOrCreate(AVG_FILE);
        avgSheet = firstSheet(avgWb);
        ensureAvgHeader(avgSheet);

        randomWb = openOrCreate(RANDOM_FILE);
        randomSheet = firstSheet(randomWb);

        sortWb = openOrCreate(SORT_FILE);
        sortSheet = firstSheet(sortWb);

        reverseSortWb = openOrCreate(REVERSE_SORT_FILE);
        reverseSortSheet = firstSheet(reverseSortWb);
    }

    // ---------- public recording API ----------

    /** Records the raw generated array for this trial into Random_Dataset.xlsx. */
    public void recordRandomArray(int trial, int[] arr) {
        writeArrayColumn(randomSheet, trial, arr);
    }

    /** Records the ascending-sorted version of the array for this trial into Sort_Dataset.xlsx. */
    public void recordSortedArray(int trial, int[] arr) {
        writeArrayColumn(sortSheet, trial, arr);
    }

    /** Records the descending-sorted version of the array for this trial into ReverseSort_Dataset.xlsx. */
    public void recordReverseSortedArray(int trial, int[] arr) {
        writeArrayColumn(reverseSortSheet, trial, arr);
    }

    /**
     * Records a single timing (in nanoseconds) into RAW_Dataset.xlsx.
     *
     * @param algorithmLabel e.g. "BubbleSort" for ascending, "BubbleSort (Reverse)" for descending
     * @param inputSize      size of the array that was sorted
     * @param trial          trial number (1-based), determines which "Trial N" column is used
     * @param timeNanos      elapsed time in nanoseconds
     */
    public void recordRawTiming(String algorithmLabel, int inputSize, int trial, long timeNanos) {
        Row row = findOrCreateRawRow(algorithmLabel, inputSize);
        int col = 1 + trial; // col 0 = Algorithm, col 1 = Input Size(n), col 2 = Trial 1, ...
        ensureRawTrialHeader(trial, col);
        row.createCell(col).setCellValue(timeNanos);
    }

    /**
     * Recomputes AVG_Sort_Dataset.xlsx from whatever is currently in RAW_Dataset.xlsx.
     * Averages every "Trial N" cell found on each Algorithm/Input Size row.
     * Only the ascending ("BubbleSort") rows are averaged into AVG_Sort_Dataset,
     * since that file has no "(Reverse)" column of its own; reverse-sort rows
     * are matched by stripping the " (Reverse)" suffix before averaging.
     */
    public void computeAverages() {
        // size -> algorithmKey -> running average data
        Map<Integer, Map<String, Double>> averages = new TreeMap<>();

        int lastRow = rawSheet.getLastRowNum();
        for (int r = 1; r <= lastRow; r++) {
            Row row = rawSheet.getRow(r);
            if (row == null) {
                continue;
            }
            String algLabel = getStringCell(row.getCell(0));
            Integer size = getIntCell(row.getCell(1));
            if (algLabel == null || size == null) {
                continue;
            }

            double sum = 0;
            int count = 0;
            int lastCol = row.getLastCellNum();
            for (int c = 2; c < lastCol; c++) {
                Double val = getDoubleCell(row.getCell(c));
                if (val != null) {
                    sum += val;
                    count++;
                }
            }
            if (count == 0) {
                continue;
            }

                    if (algLabel.endsWith("(Reverse)")) {
                    continue;
                    }
                    String algKey = algLabel.trim();
                    averages.computeIfAbsent(size, k -> new LinkedHashMap<>())
                        .put(algKey, sum / count);
        }

        for (Map.Entry<Integer, Map<String, Double>> sizeEntry : averages.entrySet()) {
            int size = sizeEntry.getKey();
            Row avgRow = findOrCreateAvgRow(size);
            avgRow.createCell(0).setCellValue(size);
            for (Map.Entry<String, Double> algEntry : sizeEntry.getValue().entrySet()) {
                int colIndex = indexOfAlgorithmKey(algEntry.getKey());
                if (colIndex >= 0) {
                    avgRow.createCell(colIndex + 1).setCellValue(algEntry.getValue());
                }
            }
        }
    }

    /** Writes all 5 workbooks back to disk. Call this once after recording everything. */
    public void saveAll() throws IOException {
        saveWorkbook(rawWb, RAW_FILE);
        saveWorkbook(avgWb, AVG_FILE);
        saveWorkbook(randomWb, RANDOM_FILE);
        saveWorkbook(sortWb, SORT_FILE);
        saveWorkbook(reverseSortWb, REVERSE_SORT_FILE);
    }

    @Override
    public void close() throws IOException {
        rawWb.close();
        avgWb.close();
        randomWb.close();
        sortWb.close();
        reverseSortWb.close();
    }

    // ---------- internals ----------

    private Workbook openOrCreate(String fileName) throws IOException {
        File file = new File(excelDir, fileName);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                return new XSSFWorkbook(fis);
            }
        }
        return new XSSFWorkbook();
    }

    private Sheet firstSheet(Workbook wb) {
        return wb.getNumberOfSheets() == 0 ? wb.createSheet("Sheet1") : wb.getSheetAt(0);
    }

    private void saveWorkbook(Workbook wb, String fileName) throws IOException {
        File file = new File(excelDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            wb.write(fos);
        }
    }

    private void ensureRawHeader(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            header = sheet.createRow(0);
        }
        if (header.getCell(0) == null) {
            header.createCell(0).setCellValue("Algorithm ");
        }
        if (header.getCell(1) == null) {
            header.createCell(1).setCellValue("Input Size(n)");
        }
    }

    private void ensureRawTrialHeader(int trial, int col) {
        Row header = rawSheet.getRow(0);
        if (header == null) {
            header = rawSheet.createRow(0);
        }
        if (header.getCell(col) == null) {
            header.createCell(col).setCellValue("Trial " + trial);
        }
    }

    private void ensureAvgHeader(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            header = sheet.createRow(0);
        }
        if (header.getCell(0) == null) {
            header.createCell(0).setCellValue("InputSize(n)");
        }
        for (int i = 0; i < AVG_ALGORITHM_HEADERS.length; i++) {
            if (header.getCell(i + 1) == null) {
                header.createCell(i + 1).setCellValue(AVG_ALGORITHM_HEADERS[i]);
            }
        }
    }

    private int indexOfAlgorithmKey(String key) {
        for (int i = 0; i < AVG_ALGORITHM_KEYS.length; i++) {
            if (AVG_ALGORITHM_KEYS[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private Row findOrCreateRawRow(String algorithmLabel, int inputSize) {
        int lastRow = rawSheet.getLastRowNum();
        for (int r = 1; r <= lastRow; r++) {
            Row row = rawSheet.getRow(r);
            if (row == null) {
                continue;
            }
            String alg = getStringCell(row.getCell(0));
            Integer size = getIntCell(row.getCell(1));
            if (algorithmLabel.equals(alg) && size != null && size == inputSize) {
                return row;
            }
        }
        Row row = rawSheet.createRow(lastRow + 1);
        row.createCell(0).setCellValue(algorithmLabel);
        row.createCell(1).setCellValue(inputSize);
        return row;
    }

    private Row findOrCreateAvgRow(int inputSize) {
        int lastRow = avgSheet.getLastRowNum();
        for (int r = 1; r <= lastRow; r++) {
            Row row = avgSheet.getRow(r);
            if (row == null) {
                continue;
            }
            Integer size = getIntCell(row.getCell(0));
            if (size != null && size == inputSize) {
                return row;
            }
        }
        return avgSheet.createRow(lastRow + 1);
    }

    private void writeArrayColumn(Sheet sheet, int trial, int[] arr) {
        Row header = sheet.getRow(0);
        if (header == null) {
            header = sheet.createRow(0);
        }
        int col = trial - 1;
        if (header.getCell(col) == null) {
            header.createCell(col).setCellValue("Trial " + trial);
        }
        for (int i = 0; i < arr.length; i++) {
            Row row = sheet.getRow(i + 1);
            if (row == null) {
                row = sheet.createRow(i + 1);
            }
            row.createCell(col).setCellValue(arr[i]);
        }
    }

    private static String getStringCell(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue());
        }
        return null;
    }

    private static Integer getIntCell(Cell cell) {
        Double d = getDoubleCell(cell);
        return d == null ? null : (int) (double) d;
    }

    private static Double getDoubleCell(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            try {
                return Double.parseDouble(cell.getStringCellValue().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
