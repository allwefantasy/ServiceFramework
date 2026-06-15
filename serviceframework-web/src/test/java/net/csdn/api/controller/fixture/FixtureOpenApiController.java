package net.csdn.api.controller.fixture;

import net.csdn.annotation.rest.Action;
import net.csdn.annotation.rest.ApiResponse;
import net.csdn.annotation.rest.At;
import net.csdn.annotation.rest.BasicInfo;
import net.csdn.annotation.rest.Contact;
import net.csdn.annotation.rest.Content;
import net.csdn.annotation.rest.ExternalDocumentation;
import net.csdn.annotation.rest.License;
import net.csdn.annotation.rest.OpenAPIDefinition;
import net.csdn.annotation.rest.Parameter;
import net.csdn.annotation.rest.Parameters;
import net.csdn.annotation.rest.Responses;
import net.csdn.annotation.rest.Schema;
import net.csdn.annotation.rest.Server;
import net.csdn.modules.http.RestRequest;

@OpenAPIDefinition(
        info = @BasicInfo(
                desc = "Fixture API",
                testParams = "id=1",
                testResult = "ok",
                contact = @Contact(name = "ServiceFramework", email = "framework@example.com", url = "https://example.com"),
                license = @License(name = "Apache-2.0", url = "https://www.apache.org/licenses/LICENSE-2.0")
        ),
        externalDocs = @ExternalDocumentation(description = "Fixture docs"),
        servers = {@Server(description = "local", url = "http://localhost")}
)
public class FixtureOpenApiController {

    @At(types = {RestRequest.Method.GET, RestRequest.Method.POST}, path = {"/fixture/{id}"})
    @Action(summary = "Fetch fixture", description = "Reads a fixture")
    @Parameters({
            @Parameter(name = "id", required = true, description = "fixture id"),
            @Parameter(name = "verbose", type = "boolean", description = "include details")
    })
    @Responses({
            @ApiResponse(
                    responseCode = "200",
                    description = "ok",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    type = "object",
                                    format = "",
                                    description = "Fixture payload",
                                    implementation = FixturePayload.class
                            )
                    )
            )
    })
    public String show() {
        return "";
    }
}
