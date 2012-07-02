package net.csdn.context;

import net.csdn.CsdnSearchIllegalArgumentException;

/**
 * User: william
 * Date: 11-10-12
 * Time: 上午10:15
 */
public enum SearchType {
    QUERY_THEN_FETCH((byte) 1),
    QUERY_AND_FETCH((byte) 3),
    SCAN((byte) 4),
    COUNT((byte) 5);

    public static final SearchType DEFAULT = QUERY_AND_FETCH;
    private byte id;

    SearchType(byte id) {
        this.id = id;
    }

    public static SearchType fromId(byte id) {
        if (id == 1) {
            return QUERY_THEN_FETCH;
        } else if (id == 3) {
            return QUERY_AND_FETCH;
        } else if (id == 4) {
            return SCAN;
        } else if (id == 5) {
            return COUNT;
        } else {
            throw new CsdnSearchIllegalArgumentException("No search type for [" + id + "]");
        }
    }

    public static SearchType fromString(String searchType) throws CsdnSearchIllegalArgumentException {
        if (searchType == null) {
            return SearchType.DEFAULT;
        }
        if ("query_then_fetch".equals(searchType)) {
            return SearchType.QUERY_THEN_FETCH;
        } else if ("query_and_fetch".equals(searchType)) {
            return SearchType.QUERY_AND_FETCH;
        } else if ("scan".equals(searchType)) {
            return SearchType.SCAN;
        } else if ("count".equals(searchType)) {
            return SearchType.COUNT;
        } else {
            throw new CsdnSearchIllegalArgumentException("No search type for [" + searchType + "]");
        }
    }

    public byte id() {
        return this.id;
    }
}
