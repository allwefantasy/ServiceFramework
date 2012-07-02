package net.csdn.modules.search;/**
 * User: WilliamZhu
 * Date: 12-5-31
 * Time: 下午8:36
 */

import net.csdn.modules.transport.data.SearchHit;

import java.util.List;

public class SearchResult {
    private List<SearchHit> datas;
    private int total;
    private int fetch_size;

    public SearchResult(List<SearchHit> datas, int total, int fetch_size) {
        this.datas = datas;
        this.total = total;
        this.fetch_size = fetch_size;
    }


    public SearchResult total(int num) {
        total += num;
        return this;
    }

    public SearchResult datas(List<SearchHit> searchHits) {
        datas.addAll(searchHits);
        return this;
    }

    public SearchResult datas(SearchHit searchHit) {
        datas.add(searchHit);
        return this;
    }

    public SearchResult merge(SearchResult searchResult) {
        datas.addAll(searchResult.getDatas());
        total += searchResult.getTotal();
        return this;
    }


    //get and set

    public List<SearchHit> getDatas() {
        return datas;
    }

    public void setDatas(List<SearchHit> datas) {
        this.datas = datas;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getFetch_size() {
        return fetch_size;
    }

    public void setFetch_size(int fetch_size) {
        this.fetch_size = fetch_size;
    }
}
