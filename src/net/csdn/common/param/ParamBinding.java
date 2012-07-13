package net.csdn.common.param;

import net.csdn.exception.ArgumentErrorException;
import org.apache.commons.beanutils.BeanUtils;

import java.util.HashMap;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-4
 * Time: 下午7:40
 */
public class ParamBinding {

    /*支持如下类型的参数绑定,目前只支持两级接口。
     a=8
     b.c=12
     b[c]=12
     [] 是为了兼容rails的表单风格。 "."则是传统Java风格
    */
    private Map<String, Map<String, String>> _children = new HashMap();
    private Map<String, String> rootValues = new HashMap<String, String>();
    private final static String keyPartDelimiter = "[\\.\\[\\]]+";

    public void toModel(Object model) {
        for (Map.Entry<String, String> entry : rootValues.entrySet()) {
            try {
                BeanUtils.setProperty(model, entry.getKey(), entry.getValue());
            } catch (Exception e) {

            }
        }
        for (Map.Entry<String, Map<String, String>> entry : _children.entrySet()) {
            Object newModel;
            try {
                Object obj = BeanUtils.getProperty(model, entry.getKey());
                if (obj != null) {
                    newModel = obj;
                } else {
                    Class clzz = model.getClass().getDeclaredField(entry.getKey()).getType();
                    newModel = clzz.newInstance();
                }

            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
            for (Map.Entry<String, String> entry1 : entry.getValue().entrySet()) {
                try {
                    BeanUtils.setProperty(newModel, entry1.getKey(), entry1.getValue());
                    BeanUtils.setProperty(model, entry.getKey(), newModel);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void parse(Map<String, String> params) {
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String[] keys = entry.getKey().split(keyPartDelimiter);
            if (keys.length > 2) throw new ArgumentErrorException("不支持超过三级层次的参数传递");
            if (keys.length == 1) {
                rootValues.put(keys[0], entry.getValue());
            } else {
                if (_children.get(keys[0]) == null) {
                    _children.put(keys[0], map(keys[1], entry.getValue()));
                } else {
                    _children.get(keys[0]).put(keys[1], entry.getValue());
                    _children.put(keys[0], _children.get(keys[0]));
                }

            }
        }
    }


}
