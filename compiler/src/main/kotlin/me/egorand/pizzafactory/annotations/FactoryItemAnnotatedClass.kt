/*
 * Copyright 2017 Egor Andreevici
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

package me.egorand.pizzafactory.annotations

import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.MirroredTypeException

class FactoryItemAnnotatedClass(internal val annotatedClassElement: TypeElement) {

  internal val id: String
  internal var qualifiedGroupClassName: String
  internal var simpleFactoryGroupName: String

  init {
    val mealAnnotation = annotatedClassElement.getAnnotation(FactoryItem::class.java)
    id = mealAnnotation.id

    if (id.isEmpty()) {
      throw ProcessingException(annotatedClassElement, "id in " +
          "@${FactoryItem::class.java.simpleName} for class " +
          "${annotatedClassElement.qualifiedName} should not be empty")
    }

    try {
      val klass = mealAnnotation.type.java
      qualifiedGroupClassName = klass.canonicalName
      simpleFactoryGroupName = klass.simpleName
    } catch (e: MirroredTypeException) {
      val classTypeMirror = e.typeMirror as DeclaredType
      val classTypeElement = classTypeMirror.asElement() as TypeElement
      qualifiedGroupClassName = classTypeElement.qualifiedName.toString()
      simpleFactoryGroupName = classTypeElement.simpleName.toString()
    }
  }
}