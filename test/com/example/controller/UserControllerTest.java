package com.example.controller;

import com.example.model.Article;
import com.example.model.Blog;
import com.example.model.User;
import net.csdn.exception.RenderFinish;
import net.csdn.jpa.JPA;
import net.csdn.junit.IocTest;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.RestResponse;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static net.csdn.common.collections.WowCollections.newHashMap;

/**
 * User: WilliamZhu
 * Date: 12-7-9
 * Time: 下午9:56
 */
public class UserControllerTest extends IocTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        User.deleteAll();
    }

    @Test
    public void testCreate() throws Exception {
        UserController userController = new UserController();
        userController.mockRequest(newHashMap("user_name", "m"), RestRequest.Method.POST, null);

        userController.m("create");

        RestResponse restResponse = userController.mockResponse();
        JSONArray array = JSONArray.fromObject(restResponse.content());
        Assert.assertTrue(array.getJSONObject(0).getString("message").equals("user_name文字太短"));

        userController.mockRequest(newHashMap("user_name", "mmmm"), RestRequest.Method.POST, null);

        userController.m("create");

        restResponse = userController.mockResponse();
        JSONObject object = JSONObject.fromObject(restResponse.content());

        Assert.assertTrue(object.get("message").equals("成功创建用户"));

        userController.mockRequest(newHashMap("user_name", "mmmm"), RestRequest.Method.POST, null);

        userController.m("create");

        restResponse = userController.mockResponse();
        array = JSONArray.fromObject(restResponse.content());

        Assert.assertTrue(array.getJSONObject(0).getString("message").equals("user_name is not uniq"));


    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        JPA.getJPAConfig().getJPAContext().closeTx(false);
    }
}
