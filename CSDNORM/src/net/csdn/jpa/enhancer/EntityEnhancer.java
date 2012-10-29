package net.csdn.jpa.enhancer;

import javassist.CtClass;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.BooleanMemberValue;
import javassist.bytecode.annotation.EnumMemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import net.csdn.common.Strings;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.BitEnhancer;
import net.csdn.jpa.JPA;
import net.csdn.jpa.type.DBInfo;
import org.hibernate.annotations.DynamicInsert;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;
import static net.csdn.common.enhancer.EnhancerHelper.createAnnotation;

/**
 * User: WilliamZhu
 * Date: 12-8-20
 * Time: 下午9:47
 */
public class EntityEnhancer implements BitEnhancer {
    private Settings settings;
    private CSLogger logger = Loggers.getLogger(getClass());

    public EntityEnhancer(Settings settings) {
        this.settings = settings;
    }

    @Override
    public void enhance(List<ModelClass> modelClasses) throws Exception {
        List<ModelClass> fatherModelClasses = new ArrayList<ModelClass>();
        for (ModelClass modelClass : modelClasses) {
            DBInfo dbInfo = JPA.dbInfo();

            CtClass ctClass = modelClass.originClass;
            Map<String, String> columns = dbInfo.tableColumns.get(modelClass.originClass.getSimpleName());
            //很有可能是继承自父类的
            if (columns != null) {
                if (!ctClass.hasAnnotation(DiscriminatorColumn.class)) {
                    for (String columnName : columns.keySet()) {
                        if (columnName.equals("discriminator")) {
                            ConstPool constPool = ctClass.getClassFile().getConstPool();


                            EnumMemberValue emb = new EnumMemberValue(constPool);
                            emb.setType("javax.persistence.DiscriminatorType");
                            emb.setValue("STRING");
                            createAnnotation(ctClass, DiscriminatorColumn.class, map(
                                    "name", new StringMemberValue(columnName, constPool),
                                    "discriminatorType", emb
                            ));

                            //@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
                            EnumMemberValue strategy = new EnumMemberValue(constPool);
                            strategy.setType("javax.persistence.InheritanceType");
                            strategy.setValue("SINGLE_TABLE");
                            createAnnotation(ctClass, Inheritance.class, map(
                                    "strategy", strategy
                            ));
                            //  createAnnotation(ctClass, MappedSuperclass.class, map());

                            fatherModelClasses.add(modelClass);
                            break;
                        }

                    }
                }
            }

        }

        for (ModelClass modelClass : fatherModelClasses) {
            ConstPool constPool = modelClass.originClass.getClassFile().getConstPool();
            createAnnotation(
                    modelClass.originClass,
                    Table.class,
                    map("name", new StringMemberValue(Strings.toUnderscoreCase(modelClass.originClass.getSimpleName()), constPool))
            );

            for (ModelClass child : modelClasses) {
                if (child.originClass.getSuperclass() == modelClass.originClass) {
                    modelClass.children.add(child);
                    //  createAnnotation(child.originClass, Table.class, map("name", new StringMemberValue(Strings.toUnderscoreCase(modelClass.originClass.getSimpleName()), constPool)));
                    createAnnotation(child.originClass, DiscriminatorValue.class, map("value", new StringMemberValue(child.originClass.getSimpleName(), constPool)));
                }
            }
        }


        for (ModelClass modelClass : modelClasses) {
            ConstPool constPool = modelClass.originClass.getClassFile().getConstPool();
            createAnnotation(modelClass.originClass, Entity.class, map());
            if (!modelClass.originClass.hasAnnotation(Table.class)) {
                createAnnotation(modelClass.originClass, Table.class, map("name", new StringMemberValue(Strings.toUnderscoreCase(modelClass.originClass.getSimpleName()), constPool)));
            }
            createAnnotation(modelClass.originClass, org.hibernate.annotations.Entity.class, map("dynamicInsert", new BooleanMemberValue(true, constPool)));
            createAnnotation(modelClass.originClass, DynamicInsert.class, map());
        }
        ModelClass.fatherModelClass.addAll(fatherModelClasses);
        ModelClass.modelClasses.addAll(modelClasses);
    }
}
