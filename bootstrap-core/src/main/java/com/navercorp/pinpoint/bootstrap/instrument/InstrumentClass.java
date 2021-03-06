/*
 * Copyright 2014 NAVER Corp.
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

package com.navercorp.pinpoint.bootstrap.instrument;

import java.util.List;

import com.navercorp.pinpoint.bootstrap.FieldAccessor;
import com.navercorp.pinpoint.bootstrap.MetadataAccessor;
import com.navercorp.pinpoint.bootstrap.interceptor.InterceptPoint;
import com.navercorp.pinpoint.bootstrap.interceptor.Interceptor;
import com.navercorp.pinpoint.bootstrap.interceptor.group.ExecutionPolicy;
import com.navercorp.pinpoint.bootstrap.interceptor.group.InterceptorGroup;

/**
 * @author emeroad
 * @author netspider
 */
public interface InstrumentClass {

    boolean isInterface();

    String getName();

    String getSuperClass();

    String[] getInterfaces();
    
    InstrumentMethod getConstructor(String... parameterTypes);

    List<InstrumentMethod> getDeclaredMethods();

    List<InstrumentMethod> getDeclaredMethods(MethodFilter filter);

    InstrumentMethod getDeclaredMethod(String name, String... parameterTypes);

    List<InstrumentClass> getNestedClasses(ClassFilter filter);    
    
    ClassLoader getClassLoader();


    public boolean isInterceptable();
    
    boolean hasConstructor(String... parameterTypes);

    boolean hasDeclaredMethod(String methodName, String... parameterTypes);
    
    boolean hasMethod(String methodName, String... parameterTypes);
    
    boolean hasEnclosingMethod(String methodName, String... parameterTypes);
    
    boolean hasField(String name, String type);
    
    boolean hasField(String name);

    
    void weave(String adviceClassName) throws InstrumentException;

    void addField(String accessorTypeName) throws InstrumentException;

    void addField(String accessorTypeName, String initValExp) throws InstrumentException;
    
    void addGetter(String getterTypeName, String fieldName) throws InstrumentException;
    
    int addInterceptor(String interceptorClassName, Object... constructorArgs) throws InstrumentException;
    
    int addInterceptor(String interceptorClassName, InterceptorGroup group, Object... constructorArgs) throws InstrumentException;

    int addInterceptor(String interceptorClassName, InterceptorGroup group, ExecutionPolicy executionPolicy, Object... constructorArgs) throws InstrumentException;
    
    int addInterceptor(MethodFilter filter, String interceptorClassName, InterceptorGroup group, ExecutionPolicy executionPolicy, Object... constructorArgs) throws InstrumentException;

    /**
     * You should check that class already have Declared method.
     * If class already have method, this method throw exception. 
     */
    InstrumentMethod addDelegatorMethod(String methodName, String... paramTypes) throws InstrumentException;
    
    byte[] toBytecode() throws InstrumentException;

    
    
    @Deprecated
    void addGetter(Class<?> getterType, String fieldName) throws InstrumentException;
    
    @Deprecated
    void addMetadata(MetadataAccessor metadata, String initialValue) throws InstrumentException;
    
    @Deprecated
    void addMetadata(MetadataAccessor metadata) throws InstrumentException;
    
    @Deprecated
    void addGetter(FieldAccessor getter, String fieldName) throws InstrumentException;
    
    @Deprecated
    void addTraceValue(Class<?> accessorType, String initialValue) throws InstrumentException;
    
    @Deprecated
    void addTraceValue(Class<?> accessorType) throws InstrumentException;

    @Deprecated
    void addGetter(String getterName, String fieldName, String fieldType) throws InstrumentException;
    
    
    @Deprecated
    int addConstructorInterceptor(String[] args, Interceptor interceptor) throws InstrumentException, NotFoundInstrumentException;

    @Deprecated
    int addConstructorInterceptor(String[] args, Interceptor interceptor, InterceptPoint type) throws InstrumentException, NotFoundInstrumentException;

    @Deprecated
    int addInterceptor(String methodName, String[] args, Interceptor interceptor) throws InstrumentException, NotFoundInstrumentException;
    
    @Deprecated
    int addInterceptor(String methodName, String[] args, Interceptor interceptor, InterceptPoint type) throws InstrumentException, NotFoundInstrumentException;

    @Deprecated
    int reuseInterceptor(String methodName, String[] args, int interceptorId) throws InstrumentException, NotFoundInstrumentException;

    @Deprecated
    int reuseInterceptor(String methodName, String[] args, int interceptorId, InterceptPoint type) throws InstrumentException, NotFoundInstrumentException;
    
    
    @Deprecated
    int addGroupInterceptor(String methodName, String[] args, Interceptor interceptor, String scopeName) throws InstrumentException, NotFoundInstrumentException;
    
    @Deprecated
    int addGroupInterceptor(String methodName, String[] args, Interceptor interceptor, InterceptorGroupDefinition scopeDefinition) throws InstrumentException;
    
    @Deprecated
    int addGroupInterceptorIfDeclared(String methodName, String[] args, Interceptor interceptor, String scopeName) throws InstrumentException;
    
    @Deprecated
    int addGroupInterceptorIfDeclared(String methodName, String[] args, Interceptor interceptor, InterceptorGroupDefinition scopeDefinition) throws InstrumentException;
    
    @Deprecated
    InstrumentClass getNestedClass(String className);

    @Deprecated
    boolean addDebugLogBeforeAfterMethod();

    @Deprecated
    boolean addDebugLogBeforeAfterConstructor();

    @Deprecated
    Class<?> toClass() throws InstrumentException;
    
    @Deprecated
    boolean insertCodeAfterConstructor(String[] args, String code);
    
    @Deprecated
    boolean insertCodeBeforeConstructor(String[] args, String code);
    
    @Deprecated
    boolean insertCodeBeforeMethod(String methodName, String[] args, String code);
    
    @Deprecated
    boolean insertCodeAfterMethod(String methodName, String[] args, String code);
    
    @Deprecated
    void addTraceVariable(String variableName, String setterName, String getterName, String variableType, String initValue) throws InstrumentException;

    @Deprecated
    void addTraceVariable(String variableName, String setterName, String getterName, String variableType) throws InstrumentException;
}
