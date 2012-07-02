package org.apache.lucene.index;

import net.csdn.common.io.Streams;
import net.csdn.common.lease.Releasable;
import net.csdn.common.lucene.ReaderSearcherHolder;
import net.csdn.exception.ArgumentErrorException;
import net.csdn.modules.http.JSONObjectUtils;
import net.sf.json.JSONObject;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static net.csdn.modules.http.JSONObjectUtils.*;

/**
 * User: william
 * Date: 11-11-29
 * Time: 下午4:26
 */
public interface Engine {
    void start() throws EngineException;

    void create(Create create) throws IOException;

    void create(Create create, boolean isCreate) throws IOException;

    void optimize() throws EngineException;

    boolean safeClose();

    ReaderSearcherHolder readerSearcherHolder();

    void flush(boolean full);

    void deleteShard();

    void refresh();

    public static interface Searcher extends Releasable {

        IndexReader reader();

        ExtendedIndexSearcher searcher();
    }


    public static class Mapper implements Serializable {
        private static final long serialVersionUID = 1L;
        private String _type;
        private Map<String, Engine.MapperType> _properties = new HashMap<String, Engine.MapperType>();
        private boolean _sourceIsEnable = true;
        private String originSource;

        public String originSource() {
            return originSource;
        }

        public MapperType _properties(String field) {
            return _properties.get(field);
        }

        public Mapper parse(String mappingContent) throws ArgumentErrorException {

            JSONObject root = JSONObject.fromObject(mappingContent);
            _type = root.keys().next().toString();
            JSONObject mapping = JSONObjectUtils.getJSONObject(root, _type);
            JSONObject source = JSONObjectUtils.getJSONObject(mapping, "_source");
            if (source == null) {
                _sourceIsEnable = false;
            } else {
                _sourceIsEnable = JSONObjectUtils.getBoolean(source, "enabled");
            }

            JSONObject properties = JSONObjectUtils.getJSONObject(mapping, "properties");
            for (Object obj : properties.keySet()) {
                String filedName = (String) obj;
                Engine.MapperType mapperType = new Engine.MapperType(properties.getJSONObject(filedName));
                _properties.put(filedName, mapperType);
            }
            originSource = mappingContent;
            return this;

        }

        public Mapper parse(File mappingFile) throws ArgumentErrorException {
            try {
                parse(Streams.copyToString(new FileReader(mappingFile)));
            } catch (Exception e) {
                throw new ArgumentErrorException("Fail to parsing mapping file [" + mappingFile.getPath() + "]");
            }
            return this;
        }

    }

    public static class MapperType implements Serializable {
        private static final long serialVersionUID = 1L;
        private String type;
        private String term_vector;
        private float boost;
        private String index;
        private boolean include_in_all;
        private String store;


        public MapperType(JSONObject object) {
            this.type = JSONObjectUtils.getString(object, "type", "string");
            this.term_vector = JSONObjectUtils.getString(object, "term_vector", "no");
            this.boost = JSONObjectUtils.getFloat(object, "boost", 1.0f);

            this.include_in_all = JSONObjectUtils.getBoolean(object, "include_in_all", false);

            if ("string".equals(type)) {
                this.index = JSONObjectUtils.getString(object, "index", "analyzed");
            } else {
                this.index = JSONObjectUtils.getString(object, "index", "not_analyzed");
            }

            this.store = JSONObjectUtils.getString(object, "store", "no");
        }

        public String type() {
            return type;
        }

        public boolean include_in_all() {
            return include_in_all;
        }

        public float boost() {
            return boost;
        }

        public Field.TermVector term_vector() {
            return Field.TermVector.valueOf(term_vector.toUpperCase());
        }

        public Field.Index index() {
            return Field.Index.valueOf(index.toUpperCase());
        }

        public Field.Store store() {
            return Field.Store.valueOf(store.toUpperCase());
        }


    }

    public static class Create {

        private final String uid;
        private final Mapper mapper;
        private final JSONObject source;
        private final Document document;

        public Create(Mapper mapper, JSONObject source) {
            this.source = source;
            this.mapper = mapper;
            if (!source.keySet().contains("id")) {
                throw new NoPrimaryKeyException("Fail to find id in source");
            }
            document = new Document();
            this.uid = mapper._type + "#" + source.getString("id");
            document.add(new Field("_uid", this.uid, Field.Store.YES, Field.Index.NOT_ANALYZED));

            buildDocument();
        }

        public Create(Document document) {
            mapper = null;
            source = null;
            this.uid = document.get("_uid");
            this.document = document;
        }


        private void buildDocument() {

            StringBuffer _allFiled = new StringBuffer();

            for (Object key : source.keySet()) {
                String fieldName = (String) key;
                Engine.MapperType mapperType = mapper._properties.get(fieldName);
                if (mapperType == null) {
                    //now we just simply  ignore.
                    continue;
                }
                if (mapperType.type.equals("float")) {
                    document.add(new NumericField(fieldName).setFloatValue(getFloat(source, fieldName)));
                } else if (mapperType.type.equals("integer")) {
                    document.add(new NumericField(fieldName).setIntValue((getInt(source, fieldName))));
                } else if (mapperType.type.equals("double")) {
                    document.add(new NumericField(fieldName).setDoubleValue(getDouble(source, fieldName)));
                } else if (mapperType.type.equals("long")) {
                    document.add(new NumericField(fieldName).setLongValue(getLong(source, fieldName)));
                } else {
                    String tempValue = source.getString(fieldName);
                    document.add(new Field(fieldName, tempValue, mapperType.store(), mapperType.index(), mapperType.term_vector()));
                    if (mapperType.include_in_all()) {
                        _allFiled.append(tempValue);
                    }
                }
            }

            String smart_type = JSONObjectUtils.getString(source, "_smart_type", "");
            if (smart_type != null) {
                document.add(new Field("_smart_type", smart_type, Field.Store.NO, Field.Index.NOT_ANALYZED_NO_NORMS, Field.TermVector.NO));
            }
            document.add(new Field("_all", _allFiled.toString(), Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
            document.add(new Field("_type", mapper._type, Field.Store.NO, Field.Index.NOT_ANALYZED_NO_NORMS, Field.TermVector.NO));
            document.add(new Field("_top", JSONObjectUtils.getString(source, "_top", ""), Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.NO));
            if (mapper._sourceIsEnable) {
                document.add(new Field("_source", source.toString(), Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS, Field.TermVector.NO));
            }
            document.setBoost(JSONObjectUtils.getFloat(source, "_boost", 1.0f));
        }


        public Document doc() {
            return document;
        }

        public String uid() {
            return uid;
        }

        public Mapper mapper() {
            return mapper;
        }

        public JSONObject source() {
            return source;
        }
    }
}
