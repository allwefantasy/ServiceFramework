package net.csdn.enhancer.association;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.annotation.StringMemberValue;
import net.csdn.common.Strings;
import net.csdn.common.enhancer.EnhancerHelper;
import net.csdn.enhancer.AssociatedHelper;
import net.csdn.jpa.JPA;
import net.csdn.jpa.enhancer.ModelClass;
import net.csdn.jpa.type.DBInfo;

import javax.persistence.JoinColumn;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;
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
                DBInfo dbInfo = JPA.dbInfo();
                Map<String, String> columns = dbInfo.tableColumns.get(ctClass.getSimpleName());
                String clzzName = AssociatedHelper.findAssociatedClassName(ctField);
                CtField mappedByField = AssociatedHelper.findAssociatedField(modelClass, clzzName);
                if (!columns.containsKey(Strings.toUnderscoreCase(ctField.getName()) + "_id")) {
                    AssociatedHelper.setMappedBy(ctField, mappedByField.getName(), "OneToOne");
                } else {
                    AssociatedHelper.setMappedBy(mappedByField, ctField.getName(), "OneToOne");
                    EnhancerHelper.createAnnotation(ctField, JoinColumn.class, map(
                            "name", new StringMemberValue(Strings.toUnderscoreCase(ctField.getName() + "_id"), ctField.getFieldInfo2().getConstPool())
                    ));
                }
                AssociatedHelper.setCascadeWithDefault(mappedByField, "OneToOne");
                AssociatedHelper.setCascadeWithDefault(ctField, "OneToOne");

                String mappedByClassName = clzzName;
                String mappedByFieldName = mappedByField.getName();

                AssociatedHelper.findAndRemoveMethod(ctClass, ctField, mappedByClassName);
                AssociatedHelper.findAndRemoveMethod(ctClass, ctField.getName());

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
