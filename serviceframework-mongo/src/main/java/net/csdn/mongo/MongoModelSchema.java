package net.csdn.mongo;

import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.MemberValue;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementRuleIds;

import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * SHA-256 of the declared document model, not of a MongoDB server schema.
 * <p>
 * The canonical text is sorted by class name, then by declared instance field
 * name. Each field contributes its type and visible mapping annotations.
 * Credentials, settings, JDBC URLs and connection strings are not inputs.
 * Annotation member values that look like secrets are replaced before hashing,
 * and the canonical text itself is not a diagnostics field.
 */
public final class MongoModelSchema {

    /**
     * Explicit diagnostics version token for Mongo enhancement events.
     * This is not a settings dump and it does not name a server database.
     */
    public static final String VERSION = "mongo-1";

    private static final String HEADER = "model-schema v1";

    private MongoModelSchema() {
    }

    public static String digest(List<CtClass> documents) {
        byte[] bytes;
        try {
            bytes = canonical(documents).getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw schemaFailure(null, "UTF-8 is unavailable", e);
        }
        return sha256(bytes);
    }

    static String canonical(List<CtClass> documents) {
        if (documents == null) {
            throw schemaFailure(null, "model schema documents are required", null);
        }
        List<CtClass> sorted = new ArrayList<CtClass>(documents);
        Collections.sort(sorted, new Comparator<CtClass>() {
            @Override
            public int compare(CtClass left, CtClass right) {
                return name(left).compareTo(name(right));
            }
        });
        StringBuilder builder = new StringBuilder();
        builder.append(HEADER);
        String previous = null;
        for (int i = 0; i < sorted.size(); i++) {
            CtClass type = sorted.get(i);
            String className = name(type);
            if (className.equals(previous)) {
                throw schemaFailure(className, "duplicate model class in schema digest", null);
            }
            previous = className;
            builder.append('\n');
            builder.append("class ").append(className);
            List<CtField> fields = declaredFields(type);
            Collections.sort(fields, new Comparator<CtField>() {
                @Override
                public int compare(CtField left, CtField right) {
                    return left.getName().compareTo(right.getName());
                }
            });
            for (int j = 0; j < fields.size(); j++) {
                CtField field = fields.get(j);
                builder.append('\n');
                builder.append("field ").append(field.getName()).append(' ');
                builder.append(typeName(type, field)).append(' ');
                builder.append(annotations(field));
            }
        }
        return builder.toString();
    }

    private static List<CtField> declaredFields(CtClass type) {
        List<CtField> fields = new ArrayList<CtField>();
        CtField[] declared = type.getDeclaredFields();
        for (int i = 0; i < declared.length; i++) {
            CtField field = declared[i];
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || (modifiers & AccessFlag.SYNTHETIC) != 0) {
                continue;
            }
            fields.add(field);
        }
        return fields;
    }

    private static String typeName(CtClass owner, CtField field) {
        try {
            return field.getType().getName();
        } catch (NotFoundException e) {
            throw schemaFailure(owner.getName(), "field type is not visible", e);
        }
    }

    private static String annotations(CtField field) {
        AnnotationsAttribute visible = (AnnotationsAttribute) field.getFieldInfo().getAttribute(AnnotationsAttribute.visibleTag);
        if (visible == null) {
            return "-";
        }
        Annotation[] annotations = visible.getAnnotations();
        if (annotations == null || annotations.length == 0) {
            return "-";
        }
        List<String> rendered = new ArrayList<String>();
        for (int i = 0; i < annotations.length; i++) {
            rendered.add(render(annotations[i]));
        }
        Collections.sort(rendered);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < rendered.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(rendered.get(i));
        }
        return builder.toString();
    }

    private static String render(Annotation annotation) {
        String typeName = annotation.getTypeName();
        Set<?> memberNames = annotation.getMemberNames();
        if (memberNames == null || memberNames.isEmpty()) {
            return typeName;
        }
        List<String> names = new ArrayList<String>();
        for (Object name : memberNames) {
            names.add(String.valueOf(name));
        }
        Collections.sort(names);
        StringBuilder builder = new StringBuilder();
        builder.append(typeName).append('(');
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            String name = names.get(i);
            builder.append(name).append('=').append(redact(annotation.getMemberValue(name)));
        }
        builder.append(')');
        return builder.toString();
    }

    private static String redact(MemberValue value) {
        String text = value == null ? "" : value.toString();
        if (secret(text)) {
            return "[redacted]";
        }
        return text;
    }

    private static boolean secret(String text) {
        String lower = text.toLowerCase(Locale.ENGLISH);
        return lower.contains("password")
                || lower.contains("passwd")
                || lower.contains("secret")
                || lower.contains("credential")
                || lower.contains("jdbc:")
                || lower.contains("mongodb://")
                || lower.contains("mongodb+srv://")
                || lower.contains("api_key")
                || lower.contains("api-key")
                || lower.contains("apikey");
    }

    private static String name(CtClass type) {
        if (type == null || type.getName() == null || type.getName().length() == 0) {
            throw schemaFailure(null, "model class name is required", null);
        }
        return type.getName();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (int i = 0; i < hash.length; i++) {
                int value = hash[i] & 0xff;
                if (value < 16) {
                    builder.append('0');
                }
                builder.append(Integer.toHexString(value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw schemaFailure(null, "model schema digest was not computed", e);
        }
    }

    private static EnhancementFailure schemaFailure(String className, String detail, Exception cause) {
        return new EnhancementFailure(
                EnhancementFailure.Category.CONFIGURATION,
                className,
                EnhancementRuleIds.MONGO_DOCUMENT,
                "diagnostics",
                detail,
                cause);
    }
}
