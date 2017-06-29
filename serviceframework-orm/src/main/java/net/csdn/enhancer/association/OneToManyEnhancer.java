package net.csdn.enhancer.association;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import net.csdn.common.enhancer.EnhancerHelper;
import net.csdn.jpa.enhancer.ModelClass;

import static net.csdn.common.logging.support.MessageFormat.format;
import static net.csdn.enhancer.AssociatedHelper.*;

/**
 * User: WilliamZhu
 * Date: 12-8-21
 * Time: 下午9:07
 */
public class OneToManyEnhancer {
    private ModelClass modelClass;

    public OneToManyEnhancer(ModelClass modelClass) {
        this.modelClass = modelClass;
    }

    public void enhancer() throws Exception {
        CtClass ctClass = modelClass.originClass;
        CtField[] fields = modelClass.originClass.getDeclaredFields();

        for (CtField ctField : fields) {
            if (EnhancerHelper.hasAnnotation(ctField, "javax.persistence.OneToMany")) {
                String clzzName = findAssociatedClassName(ctField);

                String mappedByFieldName = findAssociatedFieldName(modelClass, clzzName);
                String mappedByClassName = ctClass.getName();
                if (mappedByFieldName == null) return;

                //如果没有设置mappedBy我们帮他设置吧
                setMappedBy(ctField, mappedByFieldName, "OneToMany");
                setCascade(ctField, "OneToMany", "REFRESH", "REMOVE");


                findAndRemoveMethod(ctClass, ctField, mappedByClassName);
                findAndRemoveMethod(ctClass, ctField.getName());
                String propertyName = mappedByFieldName.substring(0, 1).toUpperCase() + mappedByFieldName.substring(1);
                String getter = "set" + propertyName;


                CtMethod ctMethod = ModelClass.findTTMethod(ctClass, ctField.getName());
                if (ctMethod != null) {
                    ctMethod.setModifiers(Modifier.PRIVATE);
                }
                ctMethod = ModelClass.findTTMethod(ctClass, ctField.getName(), new CtClass[]{
                        ctField.getType()
                });
                if (ctMethod != null) {
                    ctMethod.setModifiers(Modifier.PRIVATE);
                }

                CtMethod wow = CtMethod.make(
                        format("public net.csdn.jpa.association.Association {}() {" +
                                "net.csdn.jpa.association.Association obj = new net.csdn.jpa.association.Association(this,\"{}\",\"{}\",\"{}\");return obj;" +
                                "    }", ctField.getName(), ctField.getName(), mappedByFieldName, "javax.persistence.OneToMany"
                        )
                        ,
                        ctClass);

                ctClass.addMethod(wow);

                CtMethod wow2 = CtMethod.make(
                        format("public {} {}({} obj) {" +
                                "        this.{}.add(obj);" +
                                "        obj.{}(this);" +
                                "        return this;" +
                                "    }", ctClass.getName(), ctField.getName(), clzzName, ctField.getName(), getter
                        )
                        ,
                        ctClass);


                ctClass.addMethod(wow2);
            }
        }
    }
}
