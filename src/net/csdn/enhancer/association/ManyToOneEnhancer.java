package net.csdn.enhancer.association;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import net.csdn.enhancer.EnhancerHelper;
import net.csdn.jpa.enhancer.ModelClass;

import static net.csdn.common.logging.support.MessageFormat.format;
import static net.csdn.enhancer.AssociatedHelper.*;

/**
 * User: WilliamZhu
 * Date: 12-8-21
 * Time: 下午9:10
 */
public class ManyToOneEnhancer {

    private ModelClass modelClass;

    public ManyToOneEnhancer(ModelClass modelClass) {
        this.modelClass = modelClass;
    }

    public void enhancer() throws Exception {
        CtClass ctClass = modelClass.originClass;
        CtField[] fields = modelClass.originClass.getDeclaredFields();

        for (CtField ctField : fields) {

            if (EnhancerHelper.hasAnnotation(ctField, "javax.persistence.ManyToOne")) {

                String clzzName = ctField.getType().getName();

                String mappedByFieldName = findAssociatedFieldName(ctClass, clzzName);
                String mappedByClassName = ctClass.getName();

                //默认设置为cascade = CascadeType.PERSIST
                setCascadeWithDefault(ctField, "ManyToOne");


                findAndRemoveMethod(ctClass, ctField, mappedByClassName);
                findAndRemoveMethod(ctClass, ctField.getName());
                String propertyName = mappedByFieldName.substring(0, 1).toUpperCase() + mappedByFieldName.substring(1);
                String getter = "get" + propertyName;

                CtMethod wow = CtMethod.make(
                        format("public net.csdn.jpa.association.Association {}() {" +
                                "net.csdn.jpa.association.Association obj = new net.csdn.jpa.association.Association(this,\"{}\",\"{}\",\"{}\");return obj;" +
                                "    }", ctField.getName(), ctField.getName(), mappedByFieldName, "javax.persistence.ManyToOne"
                        ),
                        ctClass);
                ctClass.addMethod(wow);


                CtMethod wow2 = CtMethod.make(
                        format("public {} {}({} obj) {" +
                                "        this.{} = obj;" +
                                "        obj.{}().add(this);" +
                                "        return this;" +
                                "    }", ctClass.getName(), ctField.getName(), clzzName, ctField.getName(), getter
                        ),
                        ctClass);
                ctClass.addMethod(wow2);


            }
        }
    }
}
