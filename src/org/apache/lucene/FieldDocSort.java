package org.apache.lucene;

import com.google.common.collect.Lists;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.modules.transport.data.SearchHit;
import org.apache.lucene.search.SortField;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * User: william
 * Date: 11-9-27
 * Time: 下午3:26
 */
public class FieldDocSort {
      private static CSLogger logger = Loggers.getLogger(FieldDocSort.class);
    public static void sort(List<SearchHit> list){
       List topDocs = Lists.newArrayList();
       List normalDocs = Lists.newArrayList();
        for(SearchHit searchHit :list){
            if(searchHit.isTopHit()){
                topDocs.add(searchHit);
            }
            else{
                normalDocs.add(searchHit);
            }
        }
        innerScoreSort(topDocs);
        innerScoreSort(normalDocs);
        topDocs.addAll(normalDocs);
        list.clear();
        list.addAll(topDocs);
    }


    public static  void sort(List<SearchHit> list, final SortField[] fields) {
        List topDocs = Lists.newArrayList();
        List normalDocs = Lists.newArrayList();
        for(SearchHit searchHit :list){
            if(searchHit.isTopHit()){
                topDocs.add(searchHit);
            }
            else{
                normalDocs.add(searchHit);
            }
        }
        innerFieldSort(topDocs, fields);
        innerFieldSort(normalDocs, fields);
        topDocs.addAll(normalDocs);
        list.clear();
        list.addAll(topDocs);
    }

    private static void innerScoreSort(List<SearchHit> list){
        Collections.sort(list, new Comparator<SearchHit>() {
            @Override
            public int compare(SearchHit docWrapperA, SearchHit docWrapperB) {
               return (docWrapperA.getScore()-docWrapperB.getScore())>0?-1:1;
            }
        });
    }

    private static void innerFieldSort(List<SearchHit> list, final SortField[] fields){
        Collections.sort(list, new Comparator<SearchHit>() {
            @Override
            public int compare(SearchHit docWrapperA, SearchHit docWrapperB) {
                final int n = fields.length;
                int c = 0;
                for (int i = 0; i < n && c == 0; ++i) {
                    final int type = fields[i].getType();
                    if (type == SortField.STRING) {
                        final String s1 = (String) docWrapperA.getFields()[i];
                        final String s2 = (String) docWrapperB.getFields()[i];
                        // null values need to be sorted first, because of how FieldCache.getStringIndex()
                        // works - in that routine, any documents without a value in the given field are
                        // put first.  If both are null, the next SortField is used
                        if (s1 == null) {
                            c = (s2 == null) ? 0 : -1;
                        } else if (s2 == null) {
                            c = 1;
                        } else if (fields[i].getLocale() == null) {
                            c = s1.compareTo(s2);
                        }
                    } else {
                        c = docWrapperA.getFields()[i].compareTo(docWrapperB.getFields()[i]);
                    }
                    // reverse sort
                    if (fields[i].getReverse()) {
                        c = -c;
                    }else if(fields[i].getType()==SortField.SCORE){
                        c = -c;
                    }
                }
                //如果所有的域都无法相同，那么只能根据doc进行比较了
                if (c == 0) {
                    return docWrapperA.getDoc() - docWrapperB.getDoc();
                }
                return c;
            }
        });
    }
}
