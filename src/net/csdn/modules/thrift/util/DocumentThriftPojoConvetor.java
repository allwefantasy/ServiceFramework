package net.csdn.modules.thrift.util;

import net.csdn.mongo.Document;
import org.apache.thrift.TBase;

import java.util.List;

import static net.csdn.common.reflect.ReflectHelper.field;

/**
 * 6/6/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class DocumentThriftPojoConvetor {
    public void fromThriftPojoToDocument(TBase base, Document doc) {
//        for (Object key : doc.attributes().keySet()) {
//            String keyName = (String) key;
//            Object value = doc.attributes().get(keyName);
//            try {
//                Object thriftValue = field(base, keyName);
//                if(thriftValue instanceof List)
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
    }
}
