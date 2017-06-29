package net.csdn.enhancer.association;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.annotation.StringMemberValue;
import net.csdn.common.Strings;
import net.csdn.common.enhancer.EnhancerHelper;
import net.csdn.jpa.enhancer.ModelClass;

import javax.persistence.JoinColumn;
import java.util.List;

import static net.csdn.common.collections.WowCollections.map;
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

        List<CtField> wowFields = ModelClass.fields(modelClass.originClass, new ModelClass.FieldFilter() {
            @Override
            public boolean filter(CtField field) {
                return !Modifier.isStatic(field.getModifiers());
            }
        });

        for (CtField ctField : wowFields) {

            if (EnhancerHelper.hasAnnotation(ctField, "javax.persistence.ManyToOne")) {

                String clzzName = ctField.getType().getName();

                String mappedByFieldName = findAssociatedFieldName(modelClass, clzzName);
                String mappedByClassName = ctClass.getName();
                EnhancerHelper.createAnnotation(ctField, JoinColumn.class, map(
                        "name", new StringMemberValue(Strings.toUnderscoreCase(ctField.getName() + "_id"), ctField.getFieldInfo2().getConstPool())
                ));

                if (mappedByFieldName == null) return;

                //默认设置为cascade = CascadeType.PERSIST
                setCascadeWithDefault(ctField, "ManyToOne");

                findAndRemoveMethod(ctClass, ctField, mappedByClassName);
                findAndRemoveMethod(ctClass, ctField.getName());
                String propertyName = mappedByFieldName.substring(0, 1).toUpperCase() + mappedByFieldName.substring(1);
                String getter = "get" + propertyName;

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
