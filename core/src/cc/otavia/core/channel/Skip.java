/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
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

package cc.otavia.core.channel;

import java.lang.annotation.*;

/**
 * Indicates that the annotated event handler method in {@link ChannelHandler} will not be invoked by
 * {@link ChannelPipeline} and so <strong>MUST</strong> only be used when the {@link ChannelHandler}
 * method does nothing except forward to the next {@link ChannelHandler} in the pipeline.
 * <p>
 * Note that this annotation is not {@linkplain Inherited inherited}. If a user overrides a method annotated with
 * {@link Skip}, it will not be skipped anymore. Similarly, the user can override a method not annotated with
 * {@link Skip} and simply pass the event through to the next handler, which reverses the behavior of the
 * supertype.
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Skip {
}
