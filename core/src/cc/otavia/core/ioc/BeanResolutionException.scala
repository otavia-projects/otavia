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

package cc.otavia.core.ioc

class BeanResolutionException(message: String) extends IllegalStateException(message)

class BeanNotFoundException(
    val requestedType: String,
    val qualifier: Option[String],
    val registeredTypes: Seq[String]
) extends BeanResolutionException(
        s"No bean registered for [$requestedType]${qualifier.map(q => s" with qualifier [$q]").getOrElse("")}." +
            s" Registered beans: [${registeredTypes.mkString(", ")}]"
    )

class AmbiguousResolutionException(
    val requestedType: String,
    val candidates: Seq[String]
) extends BeanResolutionException(
        s"Ambiguous resolution for [$requestedType]. Candidates: [${candidates.mkString(", ")}]." +
            " Mark one as primary or use a qualifier."
    )

class DuplicateRegistrationException(
    val beanClass: String,
    val existingAddress: String,
    val newAddress: String
) extends BeanResolutionException(
        s"Duplicate registration for [$beanClass]. Existing: [$existingAddress], new: [$newAddress]"
    )

class DuplicateQualifierException(
    val qualifier: String,
    val existingBean: String,
    val newBean: String
) extends BeanResolutionException(
        s"Duplicate qualifier [$qualifier]. Used by both [$existingBean] and [$newBean]"
    )
