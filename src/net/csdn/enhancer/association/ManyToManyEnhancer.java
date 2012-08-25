package net.csdn.enhancer.association;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import net.csdn.ServiceFramwork;
import net.csdn.annotation.association.ManyToManyHint;
import net.csdn.common.Strings;
import net.csdn.enhancer.EnhancerHelper;
import net.csdn.jpa.enhancer.ModelClass;
import net.csdn.jpa.type.DBInfo;

import static net.csdn.common.logging.support.MessageFormat.format;
import static net.csdn.enhancer.AssociatedHelper.*;

/**
 * User: WilliamZhu
 * Date: 12-8-21
 * Time: 下午9:12
 */
public class ManyToManyEnhancer {
    private ModelClass modelClass;

    public ManyToManyEnhancer(ModelClass modelClass) {
        this.modelClass = modelClass;
    }

    public void enhancer() throws Exception {
        CtClass ctClass = modelClass.originClass;
        CtField[] fields = modelClass.originClass.getDeclaredFields();

        for (CtField ctField : fields) {

            if (EnhancerHelper.hasAnnotation(ctField, "javax.persistence.ManyToMany")) {
                String clzzName = findAssociatedClassName(ctField);

                String mappedByFieldName = findAssociatedFieldName(modelClass, clzzName);
                String mappedByClassName = ctClass.getName();

                CtField other = findAssociatedField(modelClass, clzzName);

                DBInfo dbInfo = ServiceFramwork.injector.getInstance(DBInfo.class);
                String otherClassSimpleName = findAssociatedClass(ctClass.getClassPool(), ctField).getSimpleName();


                String maybeTable1 = Strings.toUnderscoreCase(ctClass.getSimpleName()) + "_" + Strings.toUnderscoreCase(otherClassSimpleName);
                String maybeTable2 = Strings.toUnderscoreCase(otherClassSimpleName) + "_" + Strings.toUnderscoreCase(ctClass.getSimpleName());
                String finalTableName = dbInfo.tableNames.contains(maybeTable1) ? maybeTable1 : maybeTable2;
                setCascadeWithDefault(ctField, "ManyToMany");
                boolean isMaster = false;
                if (!ctField.hasAnnotation(ManyToManyHint.class)) {
                    if (dbInfo.tableNames.contains(maybeTable1)) {
                        setMappedBy(other, ctField.getName(), "ManyToMany");
                        isMaster = true;
                        finalTableName = maybeTable1;

                    }

                    if (dbInfo.tableNames.contains(maybeTable2)) {
                        setMappedBy(ctField, mappedByFieldName, "ManyToMany");
                        finalTableName = maybeTable2;
                    }
                    setManyToManyHint(other);
                }


                findAndRemoveMethod(ctClass, ctField, mappedByClassName);
                findAndRemoveMethod(ctClass, ctField.getName());
                String propertyName = mappedByFieldName.substring(0, 1).toUpperCase() + mappedByFieldName.substring(1);
                String getter = "get" + propertyName;

                CtMethod wow = CtMethod.make(
                        format("public net.csdn.jpa.association.Association {}() {" +
                                "net.csdn.jpa.association.Association obj = new net.csdn.jpa.association.Association(this,\"{}\",\"{}\",\"{}\",\"{}\",\"{}\");return obj;" +
                                "    }", ctField.getName(), ctField.getName(), mappedByFieldName, "javax.persistence.ManyToMany", finalTableName, isMaster
                        ),
                        ctClass);
                ctClass.addMethod(wow);

                CtMethod wow2 = CtMethod.make(
                        format("public {} {}({} obj) {" +
                                "        {}.add(obj);" +
                                "        obj.{}().add(this);" +
                                "        return this;" +
                                "    }", ctClass.getName(), ctField.getName(), clzzName, ctField.getName(), getter)
                        ,
                        ctClass);
                ctClass.addMethod(wow2);
            }
        }
    }
}
