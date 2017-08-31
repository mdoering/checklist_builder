package de.doering.dwca.utils;

import com.google.common.base.Strings;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

/**
 *
 */
public class ExcelUtils {

    public static String col(Row row, int column) {
        Cell c = row.getCell(column);
        if (c == null) {
            return null;
        }
        String val;
        switch (c.getCellType()) {
            case Cell.CELL_TYPE_NUMERIC:
                val = String.valueOf((int) c.getNumericCellValue());
                break;
            case Cell.CELL_TYPE_STRING:
                val = c.getStringCellValue();
                break;
            default:
                val = c.toString();
        }

        return Strings.nullToEmpty(val.trim()).replace("Unassigned", "");
    }

}
