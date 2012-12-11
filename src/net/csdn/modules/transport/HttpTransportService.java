package net.csdn.modules.transport;

import net.csdn.common.path.Url;
import net.csdn.modules.http.RestRequest;
import net.sf.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-5-29
 * Time: 下午5:09
 */
public interface HttpTransportService {

    public SResponse post(Url url, Map data);

    public SResponse post(final Url url, final Map data, final int timeout);

    public SResponse put(Url url, Map data);

    public SResponse http(Url url, String jsonData, RestRequest.Method method);

    public SResponse http(Url url, String jsonData, RestRequest.Method method, int timeout);

    public FutureTask<SResponse> asyncHttp(final Url url, final String jsonData, RestRequest.Method method);

    public List<SResponse> asyncHttps(final List<Url> urls, final String jsonData, RestRequest.Method method);

    class SResponse {
        private int status = 200;
        private String content;


        private Url url;

        public SResponse(int status, String content, Url url) {
            this.status = status;
            this.content = content;
            this.url = url;
        }


        public JSONObject json() {
            return JSONObject.fromObject(content);
        }

        //


        public Url getUrl() {
            return url;
        }

        public void setUrl(Url url) {
            this.url = url;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
