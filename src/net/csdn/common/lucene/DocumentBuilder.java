package net.csdn.common.lucene;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;

/**
 * User: william
 * Date: 11-9-14
 * Time: 下午5:18
 */
public class DocumentBuilder {

    public static final Document EMPTY = new Document();

    public static DocumentBuilder doc() {
        return new DocumentBuilder();
    }


    public static FieldBuilder uidField(String value, long version) {
        return field("_uid", value, Field.Store.NO, Field.Index.NOT_ANALYZED);
    }

    public static FieldBuilder field(String name, String value) {
        return field(name, value, Field.Store.YES, Field.Index.ANALYZED);
    }

    public static FieldBuilder field(String name, String value, Field.Store store, Field.Index index) {
        return new FieldBuilder(name, value, store, index);
    }

    public static FieldBuilder field(String name, String value, Field.Store store, Field.Index index, Field.TermVector termVector) {
        return new FieldBuilder(name, value, store, index, termVector);
    }

    public static FieldBuilder field(String name, byte[] value, Field.Store store) {
        return new FieldBuilder(name, value, store);
    }

    public static FieldBuilder field(String name, byte[] value, int offset, int length, Field.Store store) {
        return new FieldBuilder(name, value, offset, length, store);
    }

    private final Document document;

    private DocumentBuilder() {
        this.document = new Document();
    }

    public DocumentBuilder boost(float boost) {
        document.setBoost(boost);
        return this;
    }

    public DocumentBuilder add(Fieldable field) {
        document.add(field);
        return this;
    }

    public DocumentBuilder add(FieldBuilder fieldBuilder) {
        document.add(fieldBuilder.build());
        return this;
    }

    public Document build() {
        return document;
    }
}
