package com.example.service.blog;

import com.example.service.blog.impl.CoolBlogService;
import net.csdn.annotation.Service;

import java.util.Map;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-1
 * Time: 下午8:24
 */
@Service(implementedBy = CoolBlogService.class)
public interface BlogService {
    public String createBlog();
}
