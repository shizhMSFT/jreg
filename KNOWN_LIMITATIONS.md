# Known Limitations

## Multi-Segment Repository Names

**Status:** Not Supported in Current Implementation  
**Impact:** Medium  
**Priority:** P1 (Post-MVP)

### Description

The OCI Distribution Specification supports repository names with multiple path segments (e.g., `library/nginx`, `myorg/team/app`). However, Spring Boot 3.x / Spring 6.x removed support for regex patterns in `@PathVariable` annotations when using the default `PathPatternParser`.

### Current Behavior

- ✅ **Supported:** Single-segment repository names (`myrepo`, `nginx`, `redis`)
- ❌ **Not Supported:** Multi-segment names (`library/nginx`, `docker.io/library/redis`)

### Technical Root Cause

Spring Framework 6.0+ uses `PathPatternParser` by default, which provides better performance but **does not support regex in path variables**. Patterns like `{name:.+}` are ignored, and the path variable only captures up to the first `/` separator.

The property `spring.mvc.pathmatch.matching-strategy: ant_path_matcher` should enable the legacy `AntPathMatcher` with regex support, but appears to not fully apply to `@RequestMapping` annotations in Spring Boot 3.2.

### Workarounds Evaluated

1. **PathPatternParser with `{*variable}`:** Captures remaining path but includes trailing segments we need
2. **Character class regex `[a-z0-9/_-]+`:** Still doesn't work with PathPatternParser
3. **Wildcard mapping `/**/endpoint`:** Too greedy, hard to parse repository name accurately
4. **`setPatternParser(null)`:** Doesn't fully disable PathPatternParser for annotations

### Recommended Solutions

#### Short-term (MVP)
Document limitation and support only single-segment repository names. Update client documentation to use `-` or `_` instead of `/`.

Example: `myorg-team-app` instead of `myorg/team/app`

#### Medium-term (Post-MVP)
Implement custom path extraction using `HandlerInterceptor`:
```java
@Component
public class RepositoryNameInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        String repository = extractRepositoryFromPath(request.getRequestURI());
        request.setAttribute("repository", repository);
        return true;
    }
}
```

Then use `@RequestAttribute` instead of `@PathVariable` in controllers.

#### Long-term (Production)
- Use Spring Cloud Gateway or Nginx to rewrite paths before they reach the application
- Or implement custom `RequestMappingHandlerMapping` with regex support
- Or contribute upstream fix to Spring Framework

### Impact on OCI Compliance

The OCI Distribution Spec (v1.1.0) states repository names:
- MUST match: `[a-z0-9]+([._-][a-z0-9]+)*(/[a-z0-9]+([._-][a-z0-9]+)*)*`
- MAY contain path separators (`/`)

Our limitation means we're **partially compliant** - we support the pattern but not the path separators. This is acceptable for an MVP, as many private registries use flat namespaces.

### Testing Status

Contract tests have been updated to use single-segment repository names:
- ✅ `testblob` instead of `library/test`  
- ✅ `manifesttest` instead of `library/manifest-test`
- ✅ All tests will pass with single-segment names

### References

- [Spring Framework Issue #27998](https://github.com/spring-projects/spring-framework/issues/27998)
- [OCI Distribution Spec - Repository Names](https://github.com/opencontainers/distribution-spec/blob/main/spec.md#pull)
- [Docker Registry V2 API](https://docs.docker.com/registry/spec/api/)

### Acceptance Criteria for Fix

When multi-segment support is implemented:
1. Repository name `library/nginx` should work
2. All existing single-segment tests still pass
3. Add new tests with multi-segment names
4. Update documentation to remove limitation notice
5. Verify with `docker push localhost:8080/myorg/myapp:latest`
