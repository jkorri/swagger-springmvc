package com.mangofactory.swagger.core;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.mangofactory.swagger.scanners.ResourceGroup;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.model.ApiDescription;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.util.Set;

import static com.google.common.base.Strings.*;
import static com.google.common.collect.Sets.*;
import static com.mangofactory.swagger.core.StringUtils.*;
import static java.util.Arrays.*;

public class SpringGroupingStrategy implements ResourceGroupingStrategy {
    @Override
    public Set<ResourceGroup> getResourceGroups(RequestMappingInfo requestMappingInfo, HandlerMethod handlerMethod) {
        return groups(handlerMethod);
    }

    @Override
    public String getResourceDescription(RequestMappingInfo requestMappingInfo, HandlerMethod handlerMethod) {
        return getDescription(handlerMethod);
    }

    private Set<ResourceGroup> groups(HandlerMethod handlerMethod) {
        Class<?> controllerClass = handlerMethod.getBeanType();
        String defaultGroup = String.format("%s", splitCamelCase(controllerClass.getSimpleName(), "-"));

        Optional<RequestMapping> requestMapping
                = Optional.fromNullable(AnnotationUtils.findAnnotation(controllerClass, RequestMapping.class));
        if (requestMapping.isPresent()) {
            Set<ResourceGroup> groups = newHashSet();
            //noinspection ConstantConditions
            for(String groupFromReqMapping : asList(requestMapping.get().value())) {
                if (!isNullOrEmpty(groupFromReqMapping)) {
                    String groupName = maybeChompLeadingSlash(firstPathSegment(groupFromReqMapping));
                    groups.add(new ResourceGroup(groupName));
                }
            }
            if (groups.size() > 0) {
                return groups;
            }
        }
        return newHashSet(new ResourceGroup(maybeChompLeadingSlash(defaultGroup.toLowerCase()), "/"));
    }

    private String getDescription(HandlerMethod handlerMethod) {
        Class<?> controllerClass = handlerMethod.getBeanType();
        String description = splitCamelCase(controllerClass.getSimpleName(), " ");

        Api apiAnnotation = AnnotationUtils.findAnnotation(controllerClass, Api.class);
        if (null != apiAnnotation) {
            String descriptionFromAnnotation = Optional.fromNullable(emptyToNull(apiAnnotation.value()))
                    .or(apiAnnotation.description());
            if (!isNullOrEmpty(descriptionFromAnnotation)) {
                return descriptionFromAnnotation;
            }
        }
        return description;
    }
    
    @Override
    public Set<ApiDescription> filterApiDescriptions(SwaggerPathProvider pathProvider,
           ResourceGroup resourceGroup, Set<ApiDescription> apiDescriptions) {

        String groupPrefix = String.format("%s%s", pathProvider.getApiResourcePrefix(),
                resourceGroup.getRealUri());

        return filter(apiDescriptions, withPathBeginning(groupPrefix));
    }
    
    private Predicate<? super ApiDescription> withPathBeginning(final String path) {
        return new Predicate<ApiDescription>() {
            @Override
            public boolean apply(ApiDescription input) {
                return input.path().startsWith(path);
            }
        };
    }    
    
}
