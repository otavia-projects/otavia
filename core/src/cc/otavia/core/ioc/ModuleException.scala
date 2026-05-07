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

class ModuleException(message: String) extends IllegalStateException(message)

class ModuleDependencyException(
    val module: String,
    val missing: String,
    val loaded: Seq[String]
) extends ModuleException(
      s"Module [$module] depends on [$missing], which has not been loaded. Loaded modules: [${loaded.mkString(", ")}]"
    )

class DuplicateModuleException(
    val name: String,
    val existingClass: String
) extends ModuleException(s"Module [$name] is already loaded (from [$existingClass])")
