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

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.KotlinFile
import com.squareup.kotlinpoet.TypeName.Companion.asTypeName
import com.squareup.kotlinpoet.TypeSpec
import java.io.File
import javax.lang.model.util.Elements

class FactoryItemGroupedClasses(private val qualifiedClassName: String) {

  private val map: MutableMap<String, FactoryItemAnnotatedClass> =
      LinkedHashMap()

  @Throws(ProcessingException::class)
  operator fun plusAssign(klass: FactoryItemAnnotatedClass) {
    val existing = map[klass.id]
    if (existing != null) {
      throw ProcessingException(klass.annotatedClassElement, "Conflict: The class " +
          "${klass.annotatedClassElement.qualifiedName} is annotated with " +
          "@${FactoryItem::class.java.simpleName} with id ='${klass.id}' " +
          "but ${existing.annotatedClassElement.qualifiedName} already uses the same id")
    }
    map[klass.id] = klass
  }

  fun generateCode(elements: Elements, outputDir: File) {
    val superClassName = elements.getTypeElement(qualifiedClassName)
    val factoryClassName = superClassName.simpleName.toString() + SUFFIX
    val pkg = elements.getPackageOf(superClassName)
    val packageName = if (pkg.isUnnamed) "" else pkg.qualifiedName.toString()

    val funSpec = FunSpec.builder("create")
        .addModifiers(KModifier.PUBLIC)
        .addParameter("id", String::class)
        .returns(superClassName.asType().asTypeName())
    for (item in map.values) {
      funSpec.beginControlFlow("if (id == %S)", item.id)
          .addStatement("return %L()", item.annotatedClassElement.qualifiedName.toString())
          .endControlFlow()
    }
    funSpec.addStatement("throw IllegalArgumentException(%S)", "Unknown id = \$id")
    val typeSpec = TypeSpec.classBuilder(factoryClassName)
        .addFun(funSpec.build())
    KotlinFile.builder(packageName, factoryClassName)
        .addType(typeSpec.build())
        .build()
        .writeTo(outputDir)
  }

  companion object {
    const val SUFFIX = "Factory"
  }
}