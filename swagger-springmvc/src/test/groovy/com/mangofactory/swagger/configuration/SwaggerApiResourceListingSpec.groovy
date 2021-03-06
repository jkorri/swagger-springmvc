package com.mangofactory.swagger.configuration
import com.fasterxml.classmate.TypeResolver
import com.fasterxml.jackson.databind.ObjectMapper
import com.mangofactory.swagger.core.ClassOrApiAnnotationResourceGrouping
import com.mangofactory.swagger.core.SwaggerApiResourceListing
import com.mangofactory.swagger.core.SwaggerCache
import com.mangofactory.swagger.models.*
import com.mangofactory.swagger.ordering.ResourceListingLexicographicalOrdering
import com.mangofactory.swagger.ordering.ResourceListingPositionalOrdering
import com.mangofactory.swagger.paths.AbsoluteSwaggerPathProvider
import com.mangofactory.swagger.paths.SwaggerPathProvider
import com.mangofactory.swagger.scanners.ApiListingReferenceScanner
import com.wordnik.swagger.core.SwaggerSpec
import com.wordnik.swagger.model.*
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import spock.lang.Specification

import static com.mangofactory.swagger.ScalaUtils.*

@Mixin([com.mangofactory.swagger.mixins.RequestMappingSupport,
        com.mangofactory.swagger.mixins.SpringSwaggerConfigSupport])
class SwaggerApiResourceListingSpec extends Specification {

  def "assessors"() {
    given:
      SwaggerApiResourceListing swaggerApiResourceListing = new SwaggerApiResourceListing(null, null)
      SwaggerCache cache = new SwaggerCache()
      swaggerApiResourceListing.setSwaggerCache(cache)
      List<AuthorizationType> authTypes = Arrays.asList(new ApiKey("", ""))
      swaggerApiResourceListing.setAuthorizationTypes(authTypes)
      AbsoluteSwaggerPathProvider provider = new AbsoluteSwaggerPathProvider()
      swaggerApiResourceListing.setSwaggerPathProvider(provider);
    expect:
      cache == swaggerApiResourceListing.getSwaggerCache()
      authTypes == swaggerApiResourceListing.getAuthorizationTypes()
      provider == swaggerApiResourceListing.getSwaggerPathProvider()
  }

  def "default swagger resource"() {
    when: "I create a swagger resource"
      SwaggerCache swaggerCache = new SwaggerCache();
      SwaggerApiResourceListing swaggerApiResourceListing = new SwaggerApiResourceListing(swaggerCache, "default")
      swaggerApiResourceListing.initialize()

    then: "I should should have the correct defaults"
      ResourceListing resourceListing = swaggerCache.getResourceListing("default")
      def apiListingReferenceList = fromScalaList(resourceListing.apis())
      def authorizationTypes = fromScalaList(resourceListing.authorizations())

      resourceListing.apiVersion() == "1"
      resourceListing.swaggerVersion() == SwaggerSpec.version()

      fromOption(resourceListing.info()) == null
      apiListingReferenceList == []
      authorizationTypes == []
  }

  def "resource with api info"() {
    given:
      ApiInfo apiInfo = new ApiInfo("title", "description", "terms", "contact", "license", "licenseUrl")
    when:
      SwaggerCache swaggerCache = new SwaggerCache();
      SwaggerApiResourceListing swaggerApiResourceListing = new SwaggerApiResourceListing(swaggerCache, "default")
      swaggerApiResourceListing.apiInfo = apiInfo
      swaggerApiResourceListing.initialize()

    then:
      swaggerApiResourceListing.apiInfo.title() == "title"
      swaggerApiResourceListing.apiInfo.description() == "description"
      swaggerApiResourceListing.apiInfo.termsOfServiceUrl() == "terms"
      swaggerApiResourceListing.apiInfo.contact() == "contact"
      swaggerApiResourceListing.apiInfo.license() == "license"
      swaggerApiResourceListing.apiInfo.licenseUrl() == "licenseUrl"
  }

  def "resource with authorization types"() {
    given:
      ApiKey apiKey = new ApiKey("api_key", "header")
    when:
      SwaggerCache swaggerCache = new SwaggerCache();
      SwaggerApiResourceListing swaggerApiResourceListing = new SwaggerApiResourceListing(swaggerCache, "default")
      swaggerApiResourceListing.authorizationTypes = [apiKey]
      swaggerApiResourceListing.initialize()

    then:
      ResourceListing resourceListing = swaggerCache.getResourceListing("default")
      def authorizationTypes = fromScalaList(resourceListing.authorizations())
      def apiKeyAuthType = authorizationTypes[0]
      apiKeyAuthType instanceof ApiKey
      apiKeyAuthType.keyname == "api_key"
      apiKeyAuthType.passAs == "header"
  }

  def "resource with mocked apis"() {
    given:
      SwaggerCache swaggerCache = new SwaggerCache();
      String swaggerGroup = "swaggerGroup"
      SwaggerApiResourceListing swaggerApiResourceListing = new SwaggerApiResourceListing(swaggerCache, swaggerGroup)

      SwaggerPathProvider swaggerPathProvider = new AbsoluteSwaggerPathProvider(servletContext: servletContext())
      swaggerApiResourceListing.setSwaggerPathProvider(swaggerPathProvider)

      def settings = new SwaggerGlobalSettings()
      settings.setIgnorableParameterTypes(new SpringSwaggerConfig().defaultIgnorableParameterTypes())
      SpringSwaggerConfig springSwaggerConfig = springSwaggerConfig()
      settings.alternateTypeProvider = springSwaggerConfig.defaultAlternateTypeProvider();
      swaggerApiResourceListing.setSwaggerGlobalSettings(settings)

      def resolver = new TypeResolver()
      def modelPropertiesProvider = new DefaultModelPropertiesProvider(
              settings.alternateTypeProvider,
              new AccessorsProvider(resolver, settings.alternateTypeProvider),
              new FieldsProvider(resolver))
      modelPropertiesProvider.setObjectMapper(new ObjectMapper())

      def modelDependenciesProvider = new ModelDependencyProvider(resolver, settings.alternateTypeProvider,
              modelPropertiesProvider)
      ModelProvider modelProvider = new DefaultModelProvider(resolver, settings.alternateTypeProvider,
              modelPropertiesProvider,
              modelDependenciesProvider)

      Map handlerMethods = [(requestMappingInfo("somePath/")): dummyHandlerMethod()]
      def requestHandlerMapping = Mock(RequestMappingHandlerMapping)
      requestHandlerMapping.getHandlerMethods() >> handlerMethods

      ApiListingReferenceScanner scanner = new ApiListingReferenceScanner()
      scanner.setRequestMappingHandlerMapping([requestHandlerMapping])
      scanner.setResourceGroupingStrategy(new ClassOrApiAnnotationResourceGrouping())
      scanner.setSwaggerGroup("swaggerGroup")

      scanner.setSwaggerPathProvider(swaggerPathProvider)
      swaggerApiResourceListing.setModelProvider(modelProvider)
      swaggerApiResourceListing.setApiListingReferenceScanner(scanner)

    when:
      swaggerApiResourceListing.initialize()
      ResourceListing resourceListing = swaggerCache.getResourceListing("swaggerGroup")

    then:
      ApiListingReference apiListingReference = resourceListing.apis().head()
      apiListingReference.path() == "http://localhost:8080/context-path/api-docs/swaggerGroup/dummy-class"
      apiListingReference.position() == 0
      fromOption(apiListingReference.description()) == "Dummy Class"

    and:
      ApiListing apiListing =
            swaggerCache.swaggerApiListingMap['swaggerGroup']['dummy-class']
      apiListing.swaggerVersion() == '1.2'
      apiListing.basePath() == 'http://localhost:8080/context-path'

      /**
       * TODO - AK
       * The relative path to the resource, from the basePath, which this API Specification
       * piListing.resourcePath() == '/api-docs/swaggerGroup/com_mangofactory_swagger_dummy_DummyClass'
       */
      apiListing.resourcePath() == 'fix this'

  }

  def "Should sort based on position"() {
    given:
      SwaggerCache swaggerCache = new SwaggerCache();
      SwaggerApiResourceListing swaggerApiResourceListing = new SwaggerApiResourceListing(swaggerCache, "default")
      swaggerApiResourceListing.setApiListingReferenceOrdering(ordering)

      ApiListingReferenceScanner apiListingReferenceScanner = Mock()
      apiListingReferenceScanner.getApiListingReferences() >> [
            new ApiListingReference("/b", toOption('b'), 1),
            new ApiListingReference("/a", toOption('a'), 2)
      ]

      swaggerApiResourceListing.apiListingReferenceScanner = apiListingReferenceScanner

    when:
      swaggerApiResourceListing.initialize()
      def apis = fromScalaList(swaggerCache.getResourceListing('default').apis())
    then:
      apis[0].position == firstPosition
      apis[0].path == firstPath

    where:
      ordering                                     | firstPath | firstPosition
      new ResourceListingPositionalOrdering()      | '/b'      | 1
      new ResourceListingLexicographicalOrdering() | '/a'      | 2
  }
}
