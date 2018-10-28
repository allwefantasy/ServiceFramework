package com.example.controller.http;

import com.example.model.Tag;
import net.csdn.annotation.rest.*;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.ViewType;

/**
 * 12/25/13 WilliamZhu(allwefantasy@gmail.com)
 */
@OpenAPIDefinition(
        info = @BasicInfo(desc = "标签管理接口集合",
                contact = @Contact(url = "", name = "allwefantasy", email = "allwefantasy@gmail.com"),
                license = @License(name = "Apache", url = "...")
        ), externalDocs = @ExternalDocumentation(description = ""), servers = {
        @Server(url = "http://127.0.0.1", description = "测试服务器")
}
)
public class TagController extends ApplicationController {

    @At(path = "/tag", types = {RestRequest.Method.POST})
    @Parameters(
            @Parameter(name = "name", required = true, description = "标签的名字")
    )
    @Responses(
            @ApiResponse(
                    responseCode = "200",
                    description = "返回值为json",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "string", format = "json", description = "", implementation = Jack.class)

                    )
            )
    )
    public void save() {
        Tag tag = Tag.create(params());
        if (tag.save()) {
            render(200, "成功", ViewType.string);
        }
        render(400, "失败", ViewType.string);
    }

    @At(path = "/tag/{id}", types = {RestRequest.Method.GET})
    public void find() {
        Tag tag = Tag.where(map("name", param("id"))).singleFetch();
        if (tag == null) {
            render(404, map());
        }
        render(404, list(tag));
    }

    @At(path = "/say/hello", types = {RestRequest.Method.GET})
    public void sayHello() {
        render(200, "hello" + param("kitty"));
    }

    @At(path = "/say/hello2", types = {RestRequest.Method.POST})
    public void sayHello2() {
        render(200, "hello" + param("kitty") + "  " + request.contentAsString());
    }

}

class Jack {
    String name;
}
