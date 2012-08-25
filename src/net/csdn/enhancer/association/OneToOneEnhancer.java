package net.csdn.enhancer.association;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import net.csdn.ServiceFramwork;
import net.csdn.enhancer.AssociatedHelper;
import net.csdn.enhancer.EnhancerHelper;
import net.csdn.jpa.enhancer.ModelClass;
import net.csdn.jpa.type.DBInfo;

import java.util.Map;

import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * User: WilliamZhu
 * Date: 12-8-21
 * Time: 下午8:56
 */
public class OneToOneEnhancer {

    private ModelClass modelClass;

    public OneToOneEnhancer(ModelClass modelClass) {
        this.modelClass = modelClass;
    }

    public void enhancer() throws Exception {
        CtClass ctClass = modelClass.originClass;
        CtField[] fields = modelClass.originClass.getDeclaredFields();
        for (CtField ctField : fields) {
            if (EnhancerHelper.hasAnnotation(ctField, "javax.persistence.OneToOne")) {
                DBInfo dbInfo = ServiceFramwork.injector.getInstance(DBInfo.class);
                Map<String, String> columns = dbInfo.tableColumns.get(ctClass.getSimpleName());
                String clzzName = AssociatedHelper.findAssociatedClassName(ctField);
                CtField mappedByField = AssociatedHelper.findAssociatedField(modelClass, clzzName);
                if (!columns.containsKey(ctField.getName() + "_id")) {
                    AssociatedHelper.setMappedBy(ctField, mappedByField.getName(), "OneToOne");

                } else {
                    AssociatedHelper.setMappedBy(mappedByField, mappedByField.getName(), "OneToOne");

                }
                AssociatedHelper.setCascadeWithDefault(mappedByField, "OneToOne");
                AssociatedHelper.setCascadeWithDefault(ctField, "OneToOne");

                String mappedByClassName = clzzName;
                String mappedByFieldName = mappedByField.getName();

                AssociatedHelper.findAndRemoveMethod(ctClass, ctField, mappedByClassName);
                AssociatedHelper.findAndRemoveMethod(ctClass, ctField.getName());


                CtMethod wow = CtMethod.make(
                        format("public net.csdn.jpa.association.Association {}() {" +
                                "net.csdn.jpa.association.Association obj = new net.csdn.jpa.association.Association(this,\"{}\",\"{}\",\"{}\");return obj;" +
                                "    }", ctField.getName(), ctField.getName(), mappedByFieldName, "javax.persistence.OneToOne"
                        )
                        ,
                        ctClass);
                ctClass.addMethod(wow);


                CtMethod wow2 = CtMethod.make(
                        format("public {} {}({} obj) {" +
                                "        this.attr(\"{}\",obj);" +
                                "        obj.attr(\"{}\",this);" +
                                "        return this;" +
                                "    }", ctClass.getName(), ctField.getName(), mappedByClassName, ctField.getName(), mappedByFieldName
                        )
                        ,
                        ctClass);
                ctClass.addMethod(wow2);

            }
        }
    }
}
