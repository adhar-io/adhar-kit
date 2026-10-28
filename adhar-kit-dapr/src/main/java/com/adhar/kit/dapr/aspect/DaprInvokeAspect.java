package com.adhar.kit.dapr.aspect;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.annotation.DaprInvoke;
import com.adhar.kit.dapr.invoke.HttpVerb;
import com.adhar.kit.dapr.invoke.InvokeOptions;
import io.dapr.utils.TypeRef;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforces {@link DaprInvoke}: the annotated method's body is replaced by a service invocation
 * through the sidecar. {@code {name}} placeholders in the endpoint take the argument of that
 * parameter (URL-encoded); the first argument not used in the endpoint is the request body
 * (ignored for GET/HEAD); the response is read as the method's generic return type, or the
 * annotation's {@code responseType} when set.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Aspect
public class DaprInvokeAspect {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}/]+)}");

    private final DaprFacade daprFacade;
    private final ParameterNameDiscoverer names = new DefaultParameterNameDiscoverer();

    public DaprInvokeAspect(DaprFacade daprFacade) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
    }

    @Around("@annotation(daprInvoke)")
    public Object applyInvoke(ProceedingJoinPoint joinPoint, DaprInvoke daprInvoke) throws Throwable {
        Method method = AspectSupport.resolveMethod(joinPoint);
        Object[] args = joinPoint.getArgs();
        String[] parameterNames = method != null ? names.getParameterNames(method) : null;
        Set<Integer> usedInPath = new HashSet<>();
        String path = expand(daprInvoke.endpoint(), args, parameterNames, usedInPath);

        HttpVerb verb = HttpVerb.parse(daprInvoke.method());
        InvokeOptions options = InvokeOptions.of(verb);
        if (verb != HttpVerb.GET && verb != HttpVerb.HEAD) {
            for (int i = 0; i < args.length; i++) {
                if (!usedInPath.contains(i) && args[i] != null) {
                    options = options.withBody(args[i]);
                    break;
                }
            }
        }

        Type responseType = daprInvoke.responseType() != Object.class
                ? daprInvoke.responseType()
                : (method != null ? method.getGenericReturnType() : Object.class);
        if (responseType == void.class || responseType == Void.class) {
            daprFacade.invoke(daprInvoke.appId(), path, options);
            return null;
        }
        return daprFacade.invoke(daprInvoke.appId(), path, options, TypeRef.get(responseType));
    }

    private static String expand(String endpoint, Object[] args, String[] parameterNames, Set<Integer> used) {
        Matcher matcher = PLACEHOLDER.matcher(endpoint);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            int index = indexOf(name, parameterNames);
            if (index < 0 || index >= args.length) {
                throw new IllegalArgumentException("@DaprInvoke endpoint '" + endpoint + "' names {" + name
                        + "}, which is not a parameter of the method (compile with -parameters)");
            }
            used.add(index);
            matcher.appendReplacement(out, Matcher.quoteReplacement(
                    com.adhar.kit.dapr.http.DaprHttpClient.encodeSegment(String.valueOf(args[index]))));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static int indexOf(String name, String[] parameterNames) {
        if (parameterNames == null) {
            return -1;
        }
        for (int i = 0; i < parameterNames.length; i++) {
            if (parameterNames[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
