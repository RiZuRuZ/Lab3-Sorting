import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Manages the 7 excel files for one input-size folder (e.g. excel/10000(n)/):
 *
 *   Random_Dataset.xlsx       -> Algorithm | Input Size(n) | Trial 1..N | Average
 *                                 (time to ASCENDING-sort a RANDOM array - average case)
 *   Sort_Dataset.xlsx         -> Algorithm | Input Size(n) | Trial 1..N | Average
 *                                 (time to ASCENDING-sort an ALREADY-SORTED array - best case)
 *   ReverseSort_Dataset.xlsx  -> Algorithm | Input Size(n) | Trial 1..N | Average
 *                                 (time to DESCENDING-sort an ALREADY-SORTED array - worst case)
 *   RAW_Dataset.xlsx          -> Algorithm | Case | Input Size(n) | Trial 1..N
 *                                 (master file combining all 3 cases above)
 *   AVG_Random_Dataset.xlsx      -> InputSize(n) | BubbleSort | InsertionSort | SelectionSort | MergeSort | QuickSort
 *   AVG_Sort_Dataset.xlsx        -> same columns, pivoted from Sort_Dataset.xlsx
 *   AVG_ReverseSort_Dataset.xlsx -> same columns, pivoted from ReverseSort_Dataset.xlsx
 */
public class DatasetRecorder implements Closeable {

    public enum Case { RANDOM, SORTED, REVERSE_SORTED }

    private static final String RANDOM_FILE = "Random_Dataset.xlsx";
    private static final String SORT_FILE = "Sort_Dataset.xlsx";
    private static final String REVERSE_SORT_FILE = "ReverseSort_Dataset.xlsx";
    private static final String RAW_FILE = "RAW_Dataset.xlsx";
    private static final String AVG_RANDOM_FILE = "AVG_Random_Dataset.xlsx";
    private static final String AVG_SORT_FILE = "AVG_Sort_Dataset.xlsx";
    private static final String AVG_REVERSE_SORT_FILE = "AVG_ReverseSort_Dataset.xlsx";

    // Column order/labels used in every AVG_*.xlsx file.
    private static final String[] AVG_ALGORITHM_HEADERS = {
            "BubbleSort", "InsertionSort", "SelectionSort", "MergeSort", "QuickSort"
    };
    private static final String[] AVG_ALGORITHM_KEYS = {
            "Bubble Sort", "Insertion Sort", "Selection Sort", "Merge Sort", "Quick Sort"
    };

    private final File excelDir;

    private final Workbook randomWb;
    private final Sheet randomSheet;

    private final Workbook sortWb;
    private final Sheet sortSheet;

    private final Workbook reverseSortWb;
    private final Sheet reverseSortSheet;

    private final Workbook rawWb;
    private final Sheet rawSheet;

    private final Workbook avgRandomWb;
    private final Sheet avgRandomSheet;

    private final Workbook avgSortWb;
    private final Sheet avgSortSheet;

    private final Workbook avgReverseSortWb;
    private final Sheet avgReverseSortSheet;

    public DatasetRecorder(String excelDirPath) throws IOException {
        this.excelDir = new File(excelDirPath);
        if (!excelDir.exists()) {
            excelDir.mkdirs();
        }

        randomWb = openOrCreate(RANDOM_FILE);
        randomSheet = firstSheet(randomWb);
        ensureCaseHeader(randomSheet);

        sortWb = openOrCreate(SORT_FILE);
        sortSheet = firstSheet(sortWb);
        ensureCaseHeader(sortSheet);

        reverseSortWb = openOrCreate(REVERSE_SORT_FILE);
        reverseSortSheet = firstSheet(reverseSortWb);
        ensureCaseHeader(reverseSortSheet);

        rawWb = openOrCreate(RAW_FILE);
        rawSheet = firstSheet(rawWb);
        ensureRawHeader(rawSheet);

        avgRandomWb = openOrCreate(AVG_RANDOM_FILE);
        avgRandomSheet = firstSheet(avgRandomWb);
        ensureAvgHeader(avgRandomSheet);

        avgSortWb = openOrCreate(AVG_SORT_FILE);
        avgSortSheet = firstSheet(avgSortWb);
        ensureAvgHeader(avgSortSheet);

        avgReverseSortWb = openOrCreate(AVG_REVERSE_SORT_FILE);
        avgReverseSortSheet = firstSheet(avgReverseSortWb);
        ensureAvgHeader(avgReverseSortSheet);
    }

    // ---------- public recording API ----------

    /**
     * Looks at the "Trial N" header columns already present across Random/Sort/ReverseSort/RAW
     * and returns the next unused trial number (existing max + 1, or 1 if empty).
     * Call this once after opening, then start your trial loop from this number so
     * re-running the program APPENDS new trials instead of overwriting old ones.
     */
    public int getNextTrialNumber() {
        int max = 0;
        max = Math.max(max, maxTrialInHeader(randomSheet, 2));
        max = Math.max(max, maxTrialInHeader(sortSheet, 2));
        max = Math.max(max, maxTrialInHeader(reverseSortSheet, 2));
        max = Math.max(max, maxTrialInHeader(rawSheet, 3));
        return max + 1;
    }

    /**
     * Records one timing (in nanoseconds) for the given case, algorithm, size and trial.
     * Writes into the matching case file (Random/Sort/ReverseSort_Dataset.xlsx) AND into
     * RAW_Dataset.xlsx (the combined master file).
     */
    public void recordTiming(Case caseType, String algorithmLabel, int inputSize, int trial, long timeNanos) {
        Sheet caseSheet = sheetForCase(caseType);
        Row caseRow = findOrCreateCaseRow(caseSheet, algorithmLabel, inputSize);
        int col = 2 + (trial - 1); // 0=Algorithm, 1=Input Size(n), 2=Trial1, ...
        ensureTrialHeader(caseSheet, trial, col);
        caseRow.createCell(col).setCellValue(timeNanos);

        Row rawRow = findOrCreateRawRow(algorithmLabel, caseLabel(caseType), inputSize);
        int rawCol = 3 + (trial - 1); // 0=Algorithm, 1=Case, 2=Input Size(n), 3=Trial1, ...
        ensureTrialHeader(rawSheet, trial, rawCol);
        rawRow.createCell(rawCol).setCellValue(timeNanos);
    }

    /**
     * Recomputes everything derived from the raw trial data:
     *  - the "Average" column appended after the last Trial column in each case file
     *  - the pivoted AVG_Random/Sort/ReverseSort_Dataset.xlsx files
     */
    public void computeAverages() {
        computeCaseAverages(randomSheet, avgRandomSheet);
        computeCaseAverages(sortSheet, avgSortSheet);
        computeCaseAverages(reverseSortSheet, avgReverseSortSheet);
    }

    /** Writes all 7 workbooks back to disk. Call this once after recording everything. */
    public void saveAll() throws IOException {
        saveWorkbook(randomWb, RANDOM_FILE);
        saveWorkbook(sortWb, SORT_FILE);
        saveWorkbook(reverseSortWb, REVERSE_SORT_FILE);
        saveWorkbook(rawWb, RAW_FILE);
        saveWorkbook(avgRandomWb, AVG_RANDOM_FILE);
        saveWorkbook(avgSortWb, AVG_SORT_FILE);
        saveWorkbook(avgReverseSortWb, AVG_REVERSE_SORT_FILE);
    }

    @Override
    public void close() throws IOException {
        randomWb.close();
        sortWb.close();
        reverseSortWb.close();
        rawWb.close();
        avgRandomWb.close();
        avgSortWb.close();
        avgReverseSortWb.close();
    }

    // ---------- internals ----------

    private Sheet sheetForCase(Case caseType) {
        switch (caseType) {
            case RANDOM: return randomSheet;
            case SORTED: return sortSheet;
            case REVERSE_SORTED: return reverseSortSheet;
            default: throw new IllegalArgumentException("Unknown case: " + caseType);
        }
    }

    private String caseLabel(Case caseType) {
        switch (caseType) {
            case RANDOM: return "Random";
            case SORTED: return "Sorted";
            case REVERSE_SORTED: return "ReverseSorted";
            default: throw new IllegalArgumentException("Unknown case: " + caseType);
        }
    }

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

    private void ensureCaseHeader(Sheet sheet) {
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

    private void ensureRawHeader(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            header = sheet.createRow(0);
        }
        if (header.getCell(0) == null) {
            header.createCell(0).setCellValue("Algorithm ");
        }
        if (header.getCell(1) == null) {
            header.createCell(1).setCellValue("Case");
        }
        if (header.getCell(2) == null) {
            header.createCell(2).setCellValue("Input Size(n)");
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

    private void ensureTrialHeader(Sheet sheet, int trial, int col) {
        Row header = sheet.getRow(0);
        if (header == null) {
            header = sheet.createRow(0);
        }
        header.createCell(col).setCellValue("Trial " + trial);
    }

    private int indexOfAlgorithmKey(String key) {
        for (int i = 0; i < AVG_ALGORITHM_KEYS.length; i++) {
            if (AVG_ALGORITHM_KEYS[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private Row findOrCreateCaseRow(Sheet sheet, String algorithmLabel, int inputSize) {
        int lastRow = sheet.getLastRowNum();
        for (int r = 1; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String alg = getStringCell(row.getCell(0));
            Integer size = getIntCell(row.getCell(1));
            if (algorithmLabel.equals(alg) && size != null && size == inputSize) {
                return row;
            }
        }
        Row row = sheet.createRow(lastRow + 1);
        row.createCell(0).setCellValue(algorithmLabel);
        row.createCell(1).setCellValue(inputSize);
        return row;
    }

    private Row findOrCreateRawRow(String algorithmLabel, String caseLabel, int inputSize) {
        int lastRow = rawSheet.getLastRowNum();
        for (int r = 1; r <= lastRow; r++) {
            Row row = rawSheet.getRow(r);
            if (row == null) {
                continue;
            }
            String alg = getStringCell(row.getCell(0));
            String cs = getStringCell(row.getCell(1));
            Integer size = getIntCell(row.getCell(2));
            if (algorithmLabel.equals(alg) && caseLabel.equals(cs) && size != null && size == inputSize) {
                return row;
            }
        }
        Row row = rawSheet.createRow(lastRow + 1);
        row.createCell(0).setCellValue(algorithmLabel);
        row.createCell(1).setCellValue(caseLabel);
        row.createCell(2).setCellValue(inputSize);
        return row;
    }

    private Row findOrCreateAvgRow(Sheet avgSheet, int inputSize) {
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

    /**
     * For one case sheet (e.g. Random_Dataset.xlsx):
     *  1. Finds the last "Trial N" column actually in use.
     *  2. Writes/relocates the "Average" column to sit right after it (handles the
     *     column shifting right every time new trials get appended).
     *  3. Pivots each row's average into the matching AVG_*.xlsx sheet, keyed by size.
     */
    private void computeCaseAverages(Sheet caseSheet, Sheet avgSheet) {
        Row header = caseSheet.getRow(0);
        if (header == null) {
            return;
        }

        int lastHeaderCol = header.getLastCellNum();
        int maxTrialCol = 1; // just after the Input Size(n) column
        int oldAvgCol = -1;
        for (int c = 2; c < lastHeaderCol; c++) {
            String label = getStringCell(header.getCell(c));
            if (label == null) {
                continue;
            }
            label = label.trim();
            if (label.equalsIgnoreCase("Average")) {
                oldAvgCol = c;
            } else if (label.regionMatches(true, 0, "Trial", 0, 5)) {
                maxTrialCol = Math.max(maxTrialCol, c);
            }
        }
        int newAvgCol = maxTrialCol + 1;

        // If trials were appended since the last run, the Average column moved right -
        // clear out the stale one so we don't leave a leftover column full of old numbers.
        if (oldAvgCol != -1 && oldAvgCol != newAvgCol) {
            Cell oldHeaderCell = header.getCell(oldAvgCol);
            if (oldHeaderCell != null) {
                header.removeCell(oldHeaderCell);
            }
            for (int r = 1; r <= caseSheet.getLastRowNum(); r++) {
                Row row = caseSheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Cell oldCell = row.getCell(oldAvgCol);
                if (oldCell != null) {
                    row.removeCell(oldCell);
                }
            }
        }

        Cell avgHeaderCell = header.getCell(newAvgCol);
        if (avgHeaderCell == null) {
            avgHeaderCell = header.createCell(newAvgCol);
        }
        avgHeaderCell.setCellValue("Average");

        int lastRow = caseSheet.getLastRowNum();
        for (int r = 1; r <= lastRow; r++) {
            Row row = caseSheet.getRow(r);
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
            for (int c = 2; c <= maxTrialCol; c++) {
                Double val = getDoubleCell(row.getCell(c));
                if (val != null) {
                    sum += val;
                    count++;
                }
            }
            if (count == 0) {
                continue;
            }
            double avg = sum / count;
            row.createCell(newAvgCol).setCellValue(avg);

            int colIndex = indexOfAlgorithmKey(algLabel.trim());
            if (colIndex >= 0) {
                Row avgRow = findOrCreateAvgRow(avgSheet, size);
                avgRow.createCell(0).setCellValue(size);
                avgRow.createCell(colIndex + 1).setCellValue(avg);
            }
        }
    }

    /** Scans a sheet's header row (from startCol onward) for "Trial N" cells and returns the max N found (0 if none). */
    private int maxTrialInHeader(Sheet sheet, int startCol) {
        Row header = sheet.getRow(0);
        if (header == null) {
            return 0;
        }
        int max = 0;
        int lastCol = header.getLastCellNum();
        for (int c = startCol; c < lastCol; c++) {
            String val = getStringCell(header.getCell(c));
            if (val == null) {
                continue;
            }
            val = val.trim();
            if (val.regionMatches(true, 0, "Trial", 0, 5)) {
                try {
                    int n = Integer.parseInt(val.substring(5).trim());
                    if (n > max) {
                        max = n;
                    }
                } catch (NumberFormatException ignored) {
                    // header cell wasn't "Trial <number>", skip it (e.g. "Average")
                }
            }
        }
        return max;
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