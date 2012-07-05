package net.csdn.common.collections;

import java.util.List;

/**
 * BlogInfo: william
 * Date: 12-4-17
 * Time: 上午10:41
 */
public class WowLists {
    public static String join(List lists, String splitter) {
        StringBuffer sb = new StringBuffer();
        int len = lists.size();
        for (int i = 0; i < len; i++) {
            sb.append(lists.get(i));
            if (i != len - 1) {
                sb.append(splitter);
            }
        }
        return sb.toString();
    }

    public static String join(List lists) {
        return join(lists, ",");
    }
}
