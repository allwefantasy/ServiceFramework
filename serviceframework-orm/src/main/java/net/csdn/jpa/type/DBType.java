package net.csdn.jpa.type;

import javassist.bytecode.ConstPool;
import net.csdn.common.collect.Tuple;

import java.util.Map;


/**
 * User: WilliamZhu
 * Date: 12-7-5
 * Time: 下午8:15
 */
public interface DBType {


    public Tuple<String, String> typeToJava(String sqlType);

    public Tuple<Class, Map> dateType(String type, ConstPool constPool);

}
