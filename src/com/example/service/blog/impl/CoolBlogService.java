package com.example.service.blog.impl;

import com.example.model.Blog;
import com.example.model.User;
import com.example.service.blog.BlogService;

import java.util.Map;

import static net.csdn.common.collections.WowCollections.newHashMap;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-1
 * Time: 下午8:25
 */

public class CoolBlogService implements BlogService {
    @Override
    public String createBlog() {
        return "justForTest";
    }
}
