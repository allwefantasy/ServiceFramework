package net.csdn.api.controller

import java.util.List

import com.google.common.reflect.ClassPath
import net.csdn.ServiceFramwork
import net.csdn.annotation.rest._
import net.csdn.common.collections.WowCollections
import net.csdn.common.settings.Settings
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}
import net.csdn.common.ScalaMethodMacros.str
import org.json4s.JsonDSL._

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import scala.reflect.runtime.universe._

object APIDescAC {
  implicit val formats = org.json4s.DefaultFormats +
    new OpenAPIDefinitionSer() +
    new ParametersSer() +
    new ResponsesSer() + new ActionSer()

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
              val atAnno = m.getAnnotation(classOf[At])
              val actionAnno = m.getAnnotation(classOf[Action])
              actions += OpenAction(atAnno.types().mkString(","), atAnno.path().mkString(","), actionAnno, m.getAnnotation(classOf[Parameters]), m.getAnnotation(classOf[Responses]))

            }
            openAPIs += OpenAPI(ci.getName, a.asInstanceOf[OpenAPIDefinition], actions)
          }

        }
      }
    }
    val ser = write(openAPIs)
    ser
  }

  def classAccessors[T: TypeTag]: List[MethodSymbol] = typeOf[T].members.collect {
    case m: MethodSymbol if m.isCaseAccessor => m
  }.toList
}

case class OpenAPI(name: String, o: OpenAPIDefinition, actions: List[OpenAction])

case class OpenAction(methods: String, path: String, action: Action, parameters: Parameters, responses: Responses)


class ParametersSer extends CustomSerializer[Parameters](format => ( {
  null
}, {
  case o: Parameters => {
    JArray(o.value().map { p =>
      (str[Parameter](_.name()) -> p.name()) ~
        (str[Parameter](_.`type`()) -> p.`type`()) ~
        (str[Parameter](_.description()) -> p.description()) ~
        (str[Parameter](_.required()) -> p.required()) ~
        (str[Parameter](_.allowEmptyValue()) -> p.allowEmptyValue()) ~
        (str[Parameter](_.allowReserved()) -> p.allowReserved())
    }.toList)
  }
}))


class ActionSer extends CustomSerializer[Action](format => ( {
  null
}, {
  case o: Action => {
    (str[Action](_.summary()) -> o.summary()) ~ (str[Action](_.description()) -> o.description())
  }
}))


class ResponsesSer extends CustomSerializer[Responses](format => ( {
  null
}, {
  case o: Responses => {
    JArray(o.value().map { p =>

      val content = Extraction.decompose(p.content())(DefaultFormats + new ContentSer())
      (str[ApiResponse](_.responseCode()) -> p.responseCode()) ~
        (str[ApiResponse](_.description()) -> p.description()) ~
        (str[ApiResponse](_.content()) -> content)

    }.toList)
  }
}))


class ContentSer extends CustomSerializer[Content](format => ( {
  null
}, {
  case o: Content => {
    val schema = Extraction.decompose(o.schema())(DefaultFormats + new SchemaSer())
    (str[Content](_.mediaType()) -> JString(o.mediaType())) ~
      (str[Content](_.schema()) -> schema)
  }
}))

class SchemaSer extends CustomSerializer[Schema](format => ( {
  null
}, {
  case o: Schema => {
    val clzz = o.implementation()
    val params = clzz.getDeclaredFields.map(f => (f.getName, f.getType.getSimpleName.toLowerCase()))
    val newParams = Extraction.decompose(params)(DefaultFormats)

    (str[Schema](_.`type`()) -> JString(o.`type`())) ~
      (str[Schema](_.description()) -> JString(o.description())) ~
      (str[Schema](_.implementation()) -> newParams)
  }
}))


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
