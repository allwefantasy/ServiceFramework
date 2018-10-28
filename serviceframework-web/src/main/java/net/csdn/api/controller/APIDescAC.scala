package net.csdn.api.controller

import java.util.List

import com.alibaba.druid.sql.visitor.functions.Concat
import com.google.common.reflect.ClassPath
import net.csdn.ServiceFramwork
import net.csdn.annotation.rest._
import net.csdn.common.collections.WowCollections
import net.csdn.common.settings.Settings
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}
import net.csdn.ScalaMethodMacros._
import org.json4s.JsonDSL._

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer

object APIDescAC {
  implicit val formats = org.json4s.DefaultFormats +
    new OpenAPIDefinitionSer()

  def openAPIs(settings: Settings) = {

    var openAPIs = ArrayBuffer[OpenAPI]()

    val controllers = WowCollections.split2(settings.get("application.controller"), ",")
    controllers.map { c =>
      val classinfos = ClassPath.from(classOf[ServiceFramwork].getClassLoader).getTopLevelClassesRecursive(c)
      classinfos.map { ci =>
        val clzz = Class.forName(ci.getName)
        clzz.getAnnotations.map { a =>
          if (a.annotationType() == classOf[OpenAPIDefinition]) {
            val actions = ArrayBuffer[OpenAction]()
            clzz.getMethods.filter { m =>
              m.getAnnotation(classOf[Parameters]) != null
            }.map { m =>

              actions += OpenAction(m.getName, m.getAnnotation(classOf[Parameters]), m.getAnnotation(classOf[Responses]))

            }
            openAPIs += OpenAPI(ci.getName, a.asInstanceOf[OpenAPIDefinition], actions)
          }

        }
      }
    }
    val ser = write(openAPIs)
    ser
  }
}

case class OpenAPI(name: String, o: OpenAPIDefinition, actions: List[OpenAction])

case class OpenAction(name: String, parameters: Parameters, responses: Responses)

class OpenAPIDefinitionSer extends CustomSerializer[OpenAPIDefinition](format => ( {
  null
}, {
  case o: OpenAPIDefinition => {


    val info = Extraction.decompose(o.info())(DefaultFormats + new BasicInfoSer())
    val servers = Extraction.decompose(o.servers())(DefaultFormats + new ServerSer())
    val externalDocs = Extraction.decompose(o.externalDocs())(DefaultFormats + new ExternalDocumentationSer())

    (str[OpenAPIDefinition](_.info()) -> info) ~
      (str[OpenAPIDefinition](_.servers()) -> servers) ~
      (str[OpenAPIDefinition](_.externalDocs()) -> externalDocs)
  }
}))

class BasicInfoSer extends CustomSerializer[BasicInfo](format => ( {
  null
}, {
  case o: BasicInfo => {
    val contact = Extraction.decompose(o.contact())(DefaultFormats + new ContactSer())
    val license = Extraction.decompose(o.license())(DefaultFormats + new LicenseSer())
    val state = Extraction.decompose(o.state())(DefaultFormats + new StateSer())
    (str[BasicInfo](_.desc()) -> JString(o.desc())) ~
      (str[BasicInfo](_.testParams()) -> JString(o.testParams())) ~
      (str[BasicInfo](_.testResult()) -> JString(o.testResult())) ~
      (str[BasicInfo](_.contact()) -> contact) ~
      (str[BasicInfo](_.license()) -> license) ~
      (str[BasicInfo](_.state()) -> state)

  }
}))

class ServerSer extends CustomSerializer[Server](format => ( {
  null
}, {
  case o: Server => {
    (str[Server](_.description()) -> JString(o.description())) ~
      (str[Server](_.url()) -> JString(o.url()))
  }
}))

class ExternalDocumentationSer extends CustomSerializer[ExternalDocumentation](format => ( {
  null
}, {
  case o: ExternalDocumentation => {
    (str[ExternalDocumentation](_.description()) -> JString(o.description()))
  }
}))

class ContactSer extends CustomSerializer[Contact](format => ( {
  null
}, {
  case o: Contact => {
    (str[Contact](_.name()) -> JString(o.name())) ~
      (str[Contact](_.email()) -> JString(o.email())) ~
      (str[Contact](_.url()) -> JString(o.url()))
  }
}))

class LicenseSer extends CustomSerializer[License](format => ( {
  null
}, {
  case o: License => {
    (str[License](_.name()) -> JString(o.name())) ~
      (str[License](_.url()) -> JString(o.url()))
  }
}))

class StateSer extends CustomSerializer[State](format => ( {
  null
}, {
  case o: State => {
    (str[State](_.name()) -> JString(o.name()))

  }
}))
