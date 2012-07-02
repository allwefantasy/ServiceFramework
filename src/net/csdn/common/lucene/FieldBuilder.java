package net.csdn.common.lucene;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.document.NumericField;

/**
 * User: william
 * Date: 11-9-14
 * Time: 下午5:18
 */
public class FieldBuilder {
    private final Fieldable field;

    FieldBuilder(String name, String value, Field.Store store, Field.Index index) {
        field = new Field(name, value, store, index);
    }

    FieldBuilder(String name, String value, Field.Store store, Field.Index index, Field.TermVector termVector) {
        field = new Field(name, value, store, index, termVector);
    }

    FieldBuilder(String name, byte[] value, Field.Store store) {
        field = new Field(name, value, store);
    }


    FieldBuilder(String name, byte[] value, int offset, int length, Field.Store store) {
        field = new Field(name, value, offset, length, store);
    }


    FieldBuilder(String name, double value) {
        field = new NumericField(name).setDoubleValue(value);
    }

    FieldBuilder(String name, int value) {
        field = new NumericField(name).setIntValue(value);
    }

    FieldBuilder(String name, long value) {
        field = new NumericField(name).setLongValue(value);
    }

    FieldBuilder(String name, float value) {
        field = new NumericField(name).setFloatValue(value);
    }


    public FieldBuilder boost(float boost) {
        field.setBoost(boost);
        return this;
    }

    public FieldBuilder omitNorms(boolean omitNorms) {
        field.setOmitNorms(omitNorms);
        return this;
    }

    public FieldBuilder omitTermFreqAndPositions(boolean omitTermFreqAndPositions) {
        field.setOmitTermFreqAndPositions(omitTermFreqAndPositions);
        return this;
    }

    public Fieldable build() {
        return field;
    }

}
