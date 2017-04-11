/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.javac.ast;

import com.intellij.util.containers.Stack;
import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacRef;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

class JavacTreeRefScanner extends TreeScanner<Tree, JavacReferenceCollectorListener.ReferenceCollector> {
  private static final Set<ElementKind> ALLOWED_ELEMENTS = EnumSet.of(ElementKind.ENUM,
                                                                      ElementKind.CLASS,
                                                                      ElementKind.ANNOTATION_TYPE,
                                                                      ElementKind.INTERFACE,
                                                                      ElementKind.ENUM_CONSTANT,
                                                                      ElementKind.FIELD,
                                                                      ElementKind.CONSTRUCTOR,
                                                                      ElementKind.METHOD);

  @Override
  public Tree visitCompilationUnit(CompilationUnitTree node, JavacReferenceCollectorListener.ReferenceCollector refCollector) {
    scan(node.getPackageAnnotations(), refCollector);
    scan(node.getTypeDecls(), refCollector);
    return node;
  }

  @Override
  public Tree visitIdentifier(IdentifierTree node, JavacReferenceCollectorListener.ReferenceCollector refCollector) {
    final Element element = refCollector.getReferencedElement(node);
    if (element == null) {
      return null;
    }
    if (ALLOWED_ELEMENTS.contains(element.getKind())) {
      refCollector.sinkReference(refCollector.asJavacRef(element));
    }
    return null;
  }

  @Override
  public Tree visitNewClass(NewClassTree node, JavacReferenceCollectorListener.ReferenceCollector collector) {
    if (node.getClassBody() == null) {
      final Element element = collector.getReferencedElement(node);
      if (element != null) {
        collector.sinkReference(collector.asJavacRef(element));
      }
    }
    return super.visitNewClass(node, collector);
  }

  @Override
  public Tree visitVariable(VariableTree node, JavacReferenceCollectorListener.ReferenceCollector refCollector) {
    final Element element = refCollector.getReferencedElement(node);
    if (element != null && element.getKind() == ElementKind.FIELD) {
      final JavacRef.JavacElementRefBase ref = refCollector.asJavacRef(element);
      if (ref != null) {
        refCollector.sinkReference(ref);
        final JavacRef.JavacElementRefBase returnType = refCollector.asJavacRef(element.asType());
        if (returnType != null) {
           refCollector.sinkDeclaration(new JavacDef.JavacMemberDef(ref, returnType, isStatic(element)));
        }
      }
    }
    return super.visitVariable(node, refCollector);
  }

  @Override
  public Tree visitMemberSelect(MemberSelectTree node, JavacReferenceCollectorListener.ReferenceCollector refCollector) {
    final Element element = refCollector.getReferencedElement(node);
    if (element != null && element.getKind() != ElementKind.PACKAGE) {
      ExpressionTree qualifierExpression = node.getExpression();
      Element qualifierType = null;
      TypeMirror type = refCollector.getType(qualifierExpression);
      if (type instanceof DeclaredType) {
        qualifierType = ((DeclaredType)type).asElement();
      }
      refCollector.sinkReference(refCollector.asJavacRef(element, qualifierType));
    }
    return super.visitMemberSelect(node, refCollector);
  }

  @Override
  public Tree visitMethod(MethodTree node, JavacReferenceCollectorListener.ReferenceCollector refCollector) {
    final Element element = refCollector.getReferencedElement(node);
    if (element != null) {
      final JavacRef.JavacElementRefBase ref = refCollector.asJavacRef(element);
      if (ref != null) {
        refCollector.sinkReference(ref);
        final JavacRef.JavacElementRefBase returnType = refCollector.asJavacRef(((ExecutableElement)element).getReturnType());
        if (returnType != null) {
          refCollector.sinkDeclaration(new JavacDef.JavacMemberDef(ref, returnType, isStatic(element)));
        }
      }
    }
    return super.visitMethod(node, refCollector);
  }

  @Override
  public Tree visitMethodInvocation(MethodInvocationTree node, JavacReferenceCollectorListener.ReferenceCollector collector) {
    if (node.getMethodSelect() instanceof IdentifierTree) {
      Element element = collector.getReferencedElement(node.getMethodSelect());
      if (element.getKind() != ElementKind.CONSTRUCTOR && !element.getModifiers().contains(Modifier.STATIC)) {
        TypeElement currentClass = myCurrentEnclosingElement.peek();
        Elements elements = collector.getElementUtility();
        Types types = collector.getTypeUtility();
        TypeElement actualQualifier = null;
        while (currentClass != null) {
          List<? extends Element> members = elements.getAllMembers(currentClass);
          for (Element member : members) {
            if (member == element /*cheaper then the next condition*/ || types.isSameType(member.asType(), element.asType())) {
              actualQualifier = currentClass;
              break;
            }
          }
          if (actualQualifier != null) {
            break;
          }
          currentClass = getEnclosingClass(currentClass);
        }

        if (actualQualifier == null) {
          throw new NullPointerException();
        }
        collector.sinkReference(collector.asJavacRef(element, actualQualifier));
        scan(node.getTypeArguments(), collector);
        scan(node.getArguments(), collector);
        return null;
      }
    }
    return super.visitMethodInvocation(node, collector);
  }

  final Stack<TypeElement> myCurrentEnclosingElement = new Stack<TypeElement>();
  @Override
  public Tree visitClass(ClassTree node, JavacReferenceCollectorListener.ReferenceCollector refCollector) {
    TypeElement element = (TypeElement)refCollector.getReferencedElement(node);
    if (element == null) return null;
    myCurrentEnclosingElement.add(element);

    final TypeMirror superclass = element.getSuperclass();
    final List<? extends TypeMirror> interfaces = element.getInterfaces();
    final JavacRef[] supers;
    if (superclass != refCollector.getTypeUtility().getNoType(TypeKind.NONE)) {
      supers = new JavacRef[interfaces.size() + 1];
      final JavacRef.JavacElementRefBase ref = refCollector.asJavacRef(superclass);
      if (ref == null) return null;
      supers[interfaces.size()] = ref;

    } else {
      supers = interfaces.isEmpty() ? JavacRef.EMPTY_ARRAY : new JavacRef[interfaces.size()];
    }

    int i = 0;
    for (TypeMirror anInterface : interfaces) {
      final JavacRef.JavacElementRefBase ref = refCollector.asJavacRef(anInterface);
      if (ref == null) return null;
      supers[i++] = ref;
    }
    final JavacRef.JavacElementRefBase aClass = refCollector.asJavacRef(element);
    if (aClass == null) return null;
    refCollector.sinkReference(aClass);
    refCollector.sinkDeclaration(new JavacDef.JavacClassDef(aClass, supers));
    super.visitClass(node, refCollector);
    myCurrentEnclosingElement.pop();
    return null;
  }

  static JavacTreeRefScanner createASTScanner() {
    try {
      Class aClass = Class.forName("org.jetbrains.jps.javac.ast.Javac8RefScanner");
      return (JavacTreeRefScanner) aClass.newInstance();
    }
    catch (Throwable ignored) {
      return new JavacTreeRefScanner();
    }
  }

  private static boolean isStatic(Element element) {
    return element.getModifiers().contains(Modifier.STATIC);
  }

  private static TypeElement getEnclosingClass(TypeElement element) {
    Element current = element;
    while (true) {
      current = current.getEnclosingElement();
      if (current == null) return null;
      if (current instanceof TypeElement) return (TypeElement)current;
    }
  }
}
