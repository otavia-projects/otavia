/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.otavia.core.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares interceptors to be applied to this actor.
 * Interceptors are applied in declaration order: first listed = outermost = first to process the request.
 *
 * <p>Interceptor classes must have a public constructor whose first parameter is {@code Address[_]}.
 * The framework passes the next address in the chain to this constructor.</p>
 *
 * <p>When the actor is created with {@code num > 1} (multiple instances via RobinAddress):
 * <ul>
 *   <li>{@code perInstance = true} (default): creates one interceptor per target instance,
 *       preserving parallelism. Each interceptor-target pair runs independently.</li>
 *   <li>{@code perInstance = false}: creates a single shared interceptor wrapping the
 *       RobinAddress. Simpler but serializes all requests through one interceptor.</li>
 * </ul>
 *
 * <p>Example:
 * <pre>
 * &#64;Intercept(Array(classOf[LoggingInterceptor], classOf[AuthInterceptor]))
 * class MyHandler extends StateActor[HttpRequest] {
 *   deriveDispatch
 *   // handlers ...
 * }
 * </pre>
 *
 * <p>Execution order: Logging -&gt; Auth -&gt; MyHandler -&gt; Auth resumes -&gt; Logging resumes</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Intercept {
    Class<? extends InterceptorActor<?>>[] value();
    boolean perInstance() default true;
}
