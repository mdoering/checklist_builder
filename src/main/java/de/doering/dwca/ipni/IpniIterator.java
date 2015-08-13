package de.doering.dwca.ipni;

import java.util.Iterator;
import java.util.List;

public class IpniIterator implements Iterator<String[]> {
    private static final char DELIMITER = '%';
    private static final int HEADER_ROWS = 1;

    private static final String URI = "http://www.ipni.org/ipni/advPlantNameSearch.do?" +
            "&find_isAPNIRecord=on" +
            "&find_isAPNIRecord=false" +
            "&find_isGCIRecord=on" +
            "&find_isGCIRecord=false" +
            "&find_isIKRecord=on" +
            "&find_isIKRecord=false" +
            "&find_rankToReturn=all" +
            "&output_format=normal" +
            "&query_type=by_query" +

            "find_family=Poaceae" +
            "&chunk_size=25" +
            "&start_row=900";

    private List<String> families;

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public String[] next() {
        return null;
    }

    private class FamilyIterator implements Iterator<String> {

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public String next() {
            return null;
        }
    }
}
