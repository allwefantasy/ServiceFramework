package net.csdn.modules.deduplicate.service;

import net.csdn.modules.transport.data.SearchHit;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * User: WilliamZhu
 * Date: 12-4-28
 * Time: 上午10:34
 */
public class FingerPrintService {

    public static void result(List<SearchHit> searchHits) {
        Set<String> fieldDocWrapperSet = new HashSet<String>(searchHits.size());
        Iterator<SearchHit> iterable = searchHits.iterator();
        String fingerPrint = null;
        while (iterable.hasNext()) {
            fingerPrint = iterable.next().getObject().get("finger_print");
            if (fieldDocWrapperSet.contains(fingerPrint)) {
                iterable.remove();
            } else {
                fieldDocWrapperSet.add(fingerPrint);
            }
        }
    }

}
