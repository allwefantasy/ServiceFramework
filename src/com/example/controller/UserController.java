package com.example.controller;

import com.example.model.Article;
import com.example.model.Blog;
import com.example.model.User;
import net.csdn.annotation.At;
import net.csdn.modules.http.ApplicationController;

import static net.csdn.common.collections.WowCollections.newHashMap;
import static net.csdn.common.logging.support.MessageFormat.format;
import static net.csdn.modules.http.RestRequest.Method.POST;
import static net.csdn.modules.http.support.HttpStatus.HttpStatusBadRequest;

/**
 * User: WilliamZhu
 * Date: 12-7-9
 * Time: 下午9:23
 */
public class UserController extends ApplicationController {

    @At(path = {"/users/create"}, types = {POST})
    public void create() {
        User user = User.create(params());
        if (!user.valid()) {
            render(HttpStatusBadRequest, user.validateResults);
        }
        user.save();
        render(format(OK, "成功创建用户"));
    }

}
