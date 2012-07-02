package net.csdn.common.path;

import com.google.common.collect.Lists;
import net.csdn.common.collect.Tuple;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: william
 * Date: 11-9-26
 * Time: 上午10:31
 */
public class Url {
    private String schema = "http";
    private String host;
    private List<String> path = Lists.newArrayList();
    private int port = 80;
    private List<Tuple> query = Lists.newArrayList();

    public Url() {
    }

    public Url(URI uri) {
        this.schema = uri.getScheme();
        host = uri.getHost();
        parsePath(uri.getPath());
        parseQuery(uri.getQuery());
        this.port = uri.getPort();

    }


    public static List<Url> urls(String[] hosts, String path, String... queries) {
        List<Url> urls = new ArrayList<Url>(hosts.length);
        for (String host : hosts) {
            Url url = new Url();
            url.hostAndPort(host);
            url.path(path);
            if (queries != null) {
                for (String query : queries) {
                    url.query(query);
                }
            }
            urls.add(url);
        }
        return urls;
    }

    public static List<Url> urls(String[] hosts, String path, Map params, String... queries) {
        List<Url> urls = new ArrayList<Url>(hosts.length);
        for (String host : hosts) {
            Url url = new Url();
            url.hostAndPort(host);
            url.path(path);
            if (params != null) {
                url.query(params);
            }

            if (queries != null) {
                for (String query : queries) {
                    url.query(query);
                }
            }
            urls.add(url);
        }
        return urls;
    }

    private void parsePath(String _path) {
        if (_path == null) return;
        String[] paths = _path.split("/");
        for (String temp : paths) {
            if (temp.isEmpty()) continue;
            path.add(temp);
        }
    }

    private void parseQuery(String _query) {
        if (_query == null) return;
        String[] queries = _query.split("&");
        for (String temp : queries) {
            if (temp.isEmpty() || !temp.contains("=")) continue;
            String[] key_pair = temp.split("=");
            query.add(new Tuple(key_pair[0], key_pair[1]));
        }
    }


    public Url hostAndPort(String hostAndPort) {
        String[] hostAndPortArray = hostAndPort.split(":");
        this.host = hostAndPortArray[0];
        this.port = Integer.parseInt(hostAndPortArray[1]);
        return this;

    }

    public String hostAndPort() {
        return host + ":" + port;

    }

    public Url query(String query) {
        parseQuery(query);
        return this;
    }

    public Url query(Map query) {
        query.putAll(query);
        return this;
    }

    public Url path(String path) {
        parsePath(path);
        return this;
    }

    public Url(String url) {
        this(URI.create(url));
    }

    public Url addParam(String key, String value) {
        query.add(new Tuple(key, value));
        return this;
    }

    public String getPath() {
        String result = "";
        for (String temp : path) {
            result += ("/" + temp);
        }
        return result;
    }

    public String getQuery() {
        String result = "";
        for (Tuple<String, String> temp : query) {
            result += ("&" + temp.v1() + "=" + temp.v2());
        }
        if (result.isEmpty()) return result;
        return result.substring(1);
    }

    @Override
    public String toString() {
        String url = schema + "://" + host + ":" + port + getPath();
        String query = getQuery();
        if (query != null && !query.isEmpty()) {
            url += ("?" + query);
        }
        return url;
    }

    public URI toURI() {
        try {
            return new URI(toString());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }
}
