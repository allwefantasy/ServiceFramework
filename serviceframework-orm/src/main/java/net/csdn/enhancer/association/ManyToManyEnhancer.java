package net.csdn.enhancer.association;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.AnnotationMemberValue;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import net.csdn.annotation.association.ManyToManyHint;
import net.csdn.common.Strings;
import net.csdn.common.enhancer.EnhancerHelper;
import net.csdn.jpa.JPA;
import net.csdn.jpa.enhancer.ModelClass;
import net.csdn.jpa.type.DBInfo;

import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;

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
      /*
                @JoinTable(name="CUST_PHONE",
        joinColumns=
            @JoinColumn(name="CUST_ID", referencedColumnName="ID"),
        inverseJoinColumns=
            @JoinColumn(name="PHONE_ID", referencedColumnName="ID")
        )
    */

    private void addManyToManyAnnotation(CtField ctField, CtField other, String tableName) {
        if (ctField.hasAnnotation(JoinTable.class)) return;
        ConstPool constPool = ctField.getFieldInfo2().getConstPool();
        AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
        for (Object temp : ctField.getFieldInfo().getAttributes()) {
            AttributeInfo info = (AttributeInfo) temp;
            if (info instanceof AnnotationsAttribute) {
                attr = (AnnotationsAttribute) info;
            }
        }
        javassist.bytecode.annotation.Annotation joinTable = new javassist.bytecode.annotation.Annotation(JoinTable.class.getName(), constPool);
        joinTable.addMemberValue("name", new StringMemberValue(tableName, constPool));

        javassist.bytecode.annotation.Annotation joinColumns = new javassist.bytecode.annotation.Annotation(JoinColumn.class.getName(), constPool);
        joinColumns.addMemberValue("name", new StringMemberValue(Strings.toUnderscoreCase(other.getName() + "_id"), constPool));
        joinColumns.addMemberValue("referencedColumnName", new StringMemberValue("id", constPool));

        ArrayMemberValue t1 = new ArrayMemberValue(constPool);
        t1.setValue(new AnnotationMemberValue[]{new AnnotationMemberValue(joinColumns, constPool)});

        joinTable.addMemberValue("joinColumns", t1);

        javassist.bytecode.annotation.Annotation inverseJoinColumns = new javassist.bytecode.annotation.Annotation(JoinColumn.class.getName(), constPool);
        inverseJoinColumns.addMemberValue("name", new StringMemberValue(Strings.toUnderscoreCase(ctField.getName() + "_id"), constPool));
        inverseJoinColumns.addMemberValue("referencedColumnName", new StringMemberValue("id", constPool));

        ArrayMemberValue t2 = new ArrayMemberValue(constPool);
        t2.setValue(new AnnotationMemberValue[]{new AnnotationMemberValue(inverseJoinColumns, constPool)});

        joinTable.addMemberValue("inverseJoinColumns", t2);

        attr.addAnnotation(joinTable);
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

                DBInfo dbInfo = JPA.dbInfo();
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
                        addManyToManyAnnotation(ctField, other, maybeTable1);
                    }

                    if (dbInfo.tableNames.contains(maybeTable2)) {
                        setMappedBy(ctField, mappedByFieldName, "ManyToMany");
                        finalTableName = maybeTable2;
                        addManyToManyAnnotation(other, ctField, maybeTable2);

                    }
                    setManyToManyHint(other);
                }

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
