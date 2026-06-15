package net.csdn.api.controller

import net.csdn.common.settings.ImmutableSettings.settingsBuilder
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class APIDescACSuite extends AnyFunSuite with Matchers {

  test("openAPIs scans controller packages and serializes annotated actions") {
    val settings = settingsBuilder
      .put("application.controller", "net.csdn.api.controller.fixture")
      .build()

    val json = APIDescAC.openAPIs(settings)

    json should include("FixtureOpenApiController")
    json should include("Fixture API")
    json should include("GET,POST")
    json should include("/fixture/{id}")
    json should include("verbose")
    json should include("Fixture payload")
    json should include("application/json")
  }
}
