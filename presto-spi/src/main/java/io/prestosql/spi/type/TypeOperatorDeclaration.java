/*
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
package io.prestosql.spi.type;

import io.prestosql.spi.block.Block;
import io.prestosql.spi.connector.ConnectorSession;
import io.prestosql.spi.function.BlockIndex;
import io.prestosql.spi.function.BlockPosition;
import io.prestosql.spi.function.IcebergInvocationConvention;
import io.prestosql.spi.function.IcebergInvocationConvention.InvocationArgumentConvention;
import io.prestosql.spi.function.IcebergInvocationConvention.InvocationReturnConvention;
import io.prestosql.spi.function.IsNull;
import io.prestosql.spi.function.OperatorMethodHandle;
import io.prestosql.spi.function.OperatorType;
import io.prestosql.spi.function.ScalarOperator;
import io.prestosql.spi.function.SqlNullable;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static io.prestosql.spi.function.IcebergInvocationConvention.InvocationArgumentConvention.BLOCK_POSITION;
import static io.prestosql.spi.function.IcebergInvocationConvention.InvocationArgumentConvention.BOXED_NULLABLE;
import static io.prestosql.spi.function.IcebergInvocationConvention.InvocationArgumentConvention.NEVER_NULL;
import static io.prestosql.spi.function.IcebergInvocationConvention.InvocationArgumentConvention.NULL_FLAG;
import static io.prestosql.spi.function.IcebergInvocationConvention.InvocationReturnConvention.FAIL_ON_NULL;
import static io.prestosql.spi.function.IcebergInvocationConvention.InvocationReturnConvention.NULLABLE_RETURN;
import static io.prestosql.spi.function.IcebergInvocationConvention.simpleConvention;
import static java.lang.String.format;
import static java.lang.invoke.MethodType.methodType;
import static java.util.Objects.requireNonNull;

public final class TypeOperatorDeclaration
{
    public static final TypeOperatorDeclaration NO_TYPE_OPERATOR_DECLARATION = builder(boolean.class).build();

    private final Collection<OperatorMethodHandle> equalOperators;
    private final Collection<OperatorMethodHandle> hashCodeOperators;
    private final Collection<OperatorMethodHandle> xxHash64Operators;
    private final Collection<OperatorMethodHandle> distinctFromOperators;
    private final Collection<OperatorMethodHandle> indeterminateOperators;
    private final Collection<OperatorMethodHandle> comparisonUnorderedLastOperators;
    private final Collection<OperatorMethodHandle> comparisonUnorderedFirstOperators;
    private final Collection<OperatorMethodHandle> lessThanOperators;
    private final Collection<OperatorMethodHandle> lessThanOrEqualOperators;

    private TypeOperatorDeclaration(
            Collection<OperatorMethodHandle> equalOperators,
            Collection<OperatorMethodHandle> hashCodeOperators,
            Collection<OperatorMethodHandle> xxHash64Operators,
            Collection<OperatorMethodHandle> distinctFromOperators,
            Collection<OperatorMethodHandle> indeterminateOperators,
            Collection<OperatorMethodHandle> comparisonUnorderedLastOperators,
            Collection<OperatorMethodHandle> comparisonUnorderedFirstOperators,
            Collection<OperatorMethodHandle> lessThanOperators,
            Collection<OperatorMethodHandle> lessThanOrEqualOperators)
    {
        this.equalOperators = Collections.unmodifiableCollection(requireNonNull(equalOperators, "equalOperators is null"));
        this.hashCodeOperators = Collections.unmodifiableCollection(requireNonNull(hashCodeOperators, "hashCodeOperators is null"));
        this.xxHash64Operators = Collections.unmodifiableCollection(requireNonNull(xxHash64Operators, "xxHash64Operators is null"));
        this.distinctFromOperators = Collections.unmodifiableCollection(requireNonNull(distinctFromOperators, "distinctFromOperators is null"));
        this.indeterminateOperators = Collections.unmodifiableCollection(requireNonNull(indeterminateOperators, "indeterminateOperators is null"));
        this.comparisonUnorderedLastOperators = Collections.unmodifiableCollection(requireNonNull(comparisonUnorderedLastOperators, "comparisonUnorderedLastOperators is null"));
        this.comparisonUnorderedFirstOperators = Collections.unmodifiableCollection(requireNonNull(comparisonUnorderedFirstOperators, "comparisonUnorderedFirstOperators is null"));
        this.lessThanOperators = Collections.unmodifiableCollection(requireNonNull(lessThanOperators, "lessThanOperators is null"));
        this.lessThanOrEqualOperators = Collections.unmodifiableCollection(requireNonNull(lessThanOrEqualOperators, "lessThanOrEqualOperators is null"));
    }

    public boolean isComparable()
    {
        return !equalOperators.isEmpty();
    }

    public boolean isOrderable()
    {
        return !comparisonUnorderedLastOperators.isEmpty();
    }

    public Collection<OperatorMethodHandle> getEqualOperators()
    {
        return equalOperators;
    }

    public Collection<OperatorMethodHandle> getHashCodeOperators()
    {
        return hashCodeOperators;
    }

    public Collection<OperatorMethodHandle> getXxHash64Operators()
    {
        return xxHash64Operators;
    }

    public Collection<OperatorMethodHandle> getDistinctFromOperators()
    {
        return distinctFromOperators;
    }

    public Collection<OperatorMethodHandle> getIndeterminateOperators()
    {
        return indeterminateOperators;
    }

    public Collection<OperatorMethodHandle> getComparisonUnorderedLastOperators()
    {
        return comparisonUnorderedLastOperators;
    }

    public Collection<OperatorMethodHandle> getComparisonUnorderedFirstOperators()
    {
        return comparisonUnorderedFirstOperators;
    }

    public Collection<OperatorMethodHandle> getLessThanOperators()
    {
        return lessThanOperators;
    }

    public Collection<OperatorMethodHandle> getLessThanOrEqualOperators()
    {
        return lessThanOrEqualOperators;
    }

    public static Builder builder(Class<?> typeJavaType)
    {
        return new Builder(typeJavaType);
    }

    public static TypeOperatorDeclaration extractOperatorDeclaration(Class<?> operatorsClass, Lookup lookup, Class<?> typeJavaType)
    {
        return new Builder(typeJavaType)
                .addOperators(operatorsClass, lookup)
                .build();
    }

    public static class Builder
    {
        private final Class<?> typeJavaType;

        private final Collection<OperatorMethodHandle> equalOperators = new ArrayList<>();
        private final Collection<OperatorMethodHandle> hashCodeOperators = new ArrayList<>();
        private final Collection<OperatorMethodHandle> xxHash64Operators = new ArrayList<>();
        private final Collection<OperatorMethodHandle> distinctFromOperators = new ArrayList<>();
        private final Collection<OperatorMethodHandle> indeterminateOperators = new ArrayList<>();
        private final Collection<OperatorMethodHandle> comparisonUnorderedLastOperators = new ArrayList<>();
        private final Collection<OperatorMethodHandle> comparisonUnorderedFirstOperators = new ArrayList<>();
        private final Collection<OperatorMethodHandle> lessThanOperators = new ArrayList<>();
        private final Collection<OperatorMethodHandle> lessThanOrEqualOperators = new ArrayList<>();

        public Builder(Class<?> typeJavaType)
        {
            this.typeJavaType = requireNonNull(typeJavaType, "typeJavaType is null");
            checkArgument(!typeJavaType.equals(void.class), "void type is not supported");
        }

        public Builder addEqualOperator(OperatorMethodHandle equalOperator)
        {
            verifyMethodHandleSignature(2, boolean.class, equalOperator);
            this.equalOperators.add(equalOperator);
            return this;
        }

        public Builder addEqualOperators(Collection<OperatorMethodHandle> equalOperators)
        {
            for (OperatorMethodHandle equalOperator : equalOperators) {
                verifyMethodHandleSignature(2, boolean.class, equalOperator);
            }
            this.equalOperators.addAll(equalOperators);
            return this;
        }

        public Builder addHashCodeOperator(OperatorMethodHandle hashCodeOperator)
        {
            verifyMethodHandleSignature(1, long.class, hashCodeOperator);
            this.hashCodeOperators.add(hashCodeOperator);
            return this;
        }

        public Builder addHashCodeOperators(Collection<OperatorMethodHandle> hashCodeOperators)
        {
            for (OperatorMethodHandle hashCodeOperator : hashCodeOperators) {
                verifyMethodHandleSignature(1, long.class, hashCodeOperator);
            }
            this.hashCodeOperators.addAll(hashCodeOperators);
            return this;
        }

        public Builder addXxHash64Operator(OperatorMethodHandle xxHash64Operator)
        {
            verifyMethodHandleSignature(1, long.class, xxHash64Operator);
            this.xxHash64Operators.add(xxHash64Operator);
            return this;
        }

        public Builder addXxHash64Operators(Collection<OperatorMethodHandle> xxHash64Operators)
        {
            for (OperatorMethodHandle xxHash64Operator : xxHash64Operators) {
                verifyMethodHandleSignature(1, long.class, xxHash64Operator);
            }
            this.xxHash64Operators.addAll(xxHash64Operators);
            return this;
        }

        public Builder addDistinctFromOperator(OperatorMethodHandle distinctFromOperator)
        {
            verifyMethodHandleSignature(2, boolean.class, distinctFromOperator);
            this.distinctFromOperators.add(distinctFromOperator);
            return this;
        }

        public Builder addDistinctFromOperators(Collection<OperatorMethodHandle> distinctFromOperators)
        {
            for (OperatorMethodHandle distinctFromOperator : distinctFromOperators) {
                verifyMethodHandleSignature(2, boolean.class, distinctFromOperator);
            }
            this.distinctFromOperators.addAll(distinctFromOperators);
            return this;
        }

        public Builder addIndeterminateOperator(OperatorMethodHandle indeterminateOperator)
        {
            verifyMethodHandleSignature(1, boolean.class, indeterminateOperator);
            this.indeterminateOperators.add(indeterminateOperator);
            return this;
        }

        public Builder addIndeterminateOperators(Collection<OperatorMethodHandle> indeterminateOperators)
        {
            for (OperatorMethodHandle indeterminateOperator : indeterminateOperators) {
                verifyMethodHandleSignature(1, boolean.class, indeterminateOperator);
            }
            this.indeterminateOperators.addAll(indeterminateOperators);
            return this;
        }

        public Builder addComparisonUnorderedLastOperator(OperatorMethodHandle comparisonOperator)
        {
            verifyMethodHandleSignature(2, long.class, comparisonOperator);
            this.comparisonUnorderedLastOperators.add(comparisonOperator);
            return this;
        }

        public Builder addComparisonUnorderedLastOperators(Collection<OperatorMethodHandle> comparisonOperators)
        {
            for (OperatorMethodHandle comparisonOperator : comparisonOperators) {
                verifyMethodHandleSignature(2, long.class, comparisonOperator);
            }
            this.comparisonUnorderedLastOperators.addAll(comparisonOperators);
            return this;
        }

        public Builder addComparisonUnorderedFirstOperator(OperatorMethodHandle comparisonOperator)
        {
            verifyMethodHandleSignature(2, long.class, comparisonOperator);
            this.comparisonUnorderedFirstOperators.add(comparisonOperator);
            return this;
        }

        public Builder addComparisonUnorderedFirstOperators(Collection<OperatorMethodHandle> comparisonOperators)
        {
            for (OperatorMethodHandle comparisonOperator : comparisonOperators) {
                verifyMethodHandleSignature(2, long.class, comparisonOperator);
            }
            this.comparisonUnorderedFirstOperators.addAll(comparisonOperators);
            return this;
        }

        public Builder addLessThanOrEqualOperator(OperatorMethodHandle lessThanOrEqualOperator)
        {
            verifyMethodHandleSignature(2, boolean.class, lessThanOrEqualOperator);
            this.lessThanOrEqualOperators.add(lessThanOrEqualOperator);
            return this;
        }

        public Builder addLessThanOrEqualOperators(Collection<OperatorMethodHandle> lessThanOrEqualOperators)
        {
            for (OperatorMethodHandle lessThanOrEqualOperator : lessThanOrEqualOperators) {
                verifyMethodHandleSignature(2, boolean.class, lessThanOrEqualOperator);
            }
            this.lessThanOrEqualOperators.addAll(lessThanOrEqualOperators);
            return this;
        }

        public Builder addLessThanOperator(OperatorMethodHandle lessThanOperator)
        {
            verifyMethodHandleSignature(2, boolean.class, lessThanOperator);
            this.lessThanOperators.add(lessThanOperator);
            return this;
        }

        public Builder addLessThanOperators(Collection<OperatorMethodHandle> lessThanOperators)
        {
            for (OperatorMethodHandle lessThanOperator : lessThanOperators) {
                verifyMethodHandleSignature(2, boolean.class, lessThanOperator);
            }
            this.lessThanOperators.addAll(lessThanOperators);
            return this;
        }

        public Builder addOperators(Class<?> operatorsClass, Lookup lookup)
        {
            boolean addedOperator = false;
            for (Method method : operatorsClass.getDeclaredMethods()) {
                ScalarOperator scalarOperator = method.getAnnotation(ScalarOperator.class);
                if (scalarOperator == null) {
                    continue;
                }
                OperatorType operatorType = scalarOperator.value();

                MethodHandle methodHandle;
                try {
                    methodHandle = lookup.unreflect(method);
                }
                catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

                switch (operatorType) {
                    case EQUAL:
                        addEqualOperator(new OperatorMethodHandle(parseInvocationConvention(operatorType, typeJavaType, method, boolean.class), methodHandle));
                        break;
                    case HASH_CODE:
                        addHashCodeOperator(new OperatorMethodHandle(parseInvocationConvention(operatorType, typeJavaType, method, long.class), methodHandle));
                        break;
                    case XX_HASH_64:
                        addXxHash64Operator(new OperatorMethodHandle(parseInvocationConvention(operatorType, typeJavaType, method, long.class), methodHandle));
                        break;
                    case IS_DISTINCT_FROM:
                        addDistinctFromOperator(new OperatorMethodHandle(parseInvocationConvention(operatorType, typeJavaType, method, boolean.class), methodHandle));
                        break;
                    case INDETERMINATE:
                        addIndeterminateOperator(new OperatorMethodHandle(parseInvocationConvention(operatorType, typeJavaType, method, boolean.class), methodHandle));
                        break;
                    case COMPARISON_UNORDERED_LAST:
                        addComparisonUnorderedLastOperator(new OperatorMethodHandle(parseInvocationConvention(operatorType, typeJavaType, method, long.class), methodHandle));
                        break;
                    case COMPARISON_UNORDERED_FIRST:
                        addComparisonUnorderedFirstOperator(new OperatorMethodHandle(parseInvocationConvention(operatorType, typeJavaType, method, long.class), methodHandle));
                        break;
                    case LESS_THAN:
                        addLessThanOperator(new OperatorMethodHandle(parseInvocationConvention(operatorType, typeJavaType, method, boolean.class), methodHandle));
                        break;
                    case LESS_THAN_OR_EQUAL:
                        addLessThanOrEqualOperator(new OperatorMethodHandle(parseInvocationConvention(operatorType, typeJavaType, method, boolean.class), methodHandle));
                        break;
                    default:
                        throw new IllegalArgumentException(operatorType + " operator is not supported: " + method);
                }
                addedOperator = true;
            }
            if (!addedOperator) {
                throw new IllegalArgumentException(operatorsClass + " does not contain any operators");
            }
            return this;
        }

        private void verifyMethodHandleSignature(int expectedArgumentCount, Class<?> returnJavaType, OperatorMethodHandle operatorMethodHandle)
        {
            MethodType methodType = operatorMethodHandle.getMethodHandle().type();
            IcebergInvocationConvention convention = operatorMethodHandle.getCallingConvention();

            checkArgument(convention.getArgumentConventions().size() == expectedArgumentCount,
                    "Expected %s arguments, but got %s", expectedArgumentCount, convention.getArgumentConventions().size());

            checkArgument(methodType.parameterList().stream().noneMatch(ConnectorSession.class::equals),
                    "Session is not supported in type operators");

            int expectedParameterCount = convention.getArgumentConventions().stream()
                    .mapToInt(InvocationArgumentConvention::getParameterCount)
                    .sum();
            checkArgument(expectedParameterCount == methodType.parameterCount(),
                    "Expected %s method parameters, but got %s", expectedParameterCount, methodType.parameterCount());

            int parameterIndex = 0;
            for (InvocationArgumentConvention argumentConvention : convention.getArgumentConventions()) {
                Class<?> parameterType = methodType.parameterType(parameterIndex);
                checkArgument(!parameterType.equals(ConnectorSession.class), "Session is not supported in type operators");
                switch (argumentConvention) {
                    case NEVER_NULL:
                        checkArgument(parameterType.isAssignableFrom(typeJavaType),
                                "Expected argument type to be %s, but is %s", typeJavaType, parameterType);
                        break;
                    case NULL_FLAG:
                        checkArgument(parameterType.isAssignableFrom(typeJavaType),
                                "Expected argument type to be %s, but is %s", typeJavaType, parameterType);
                        checkArgument(methodType.parameterType(parameterIndex + 1).equals(boolean.class),
                                "Expected null flag parameter to be followed by a boolean parameter");
                        break;
                    case BOXED_NULLABLE:
                        checkArgument(parameterType.isAssignableFrom(wrap(typeJavaType)),
                                "Expected argument type to be %s, but is %s", wrap(typeJavaType), parameterType);
                        break;
                    case BLOCK_POSITION:
                        checkArgument(parameterType.equals(Block.class) && methodType.parameterType(parameterIndex + 1).equals(int.class),
                                "Expected BLOCK_POSITION argument have parameters Block and int");
                        break;
                    case FUNCTION:
                        throw new IllegalArgumentException("Function argument convention is not supported in type operators");
                    default:
                        throw new UnsupportedOperationException("Unknown argument convention: " + argumentConvention);
                }
                parameterIndex += argumentConvention.getParameterCount();
            }

            InvocationReturnConvention returnConvention = convention.getReturnConvention();
            switch (returnConvention) {
                case FAIL_ON_NULL:
                    checkArgument(methodType.returnType().equals(returnJavaType),
                            "Expected return type to be %s, but is %s", returnJavaType, methodType.returnType());
                    break;
                case NULLABLE_RETURN:
                    checkArgument(methodType.returnType().equals(wrap(returnJavaType)),
                            "Expected return type to be %s, but is %s", returnJavaType, wrap(methodType.returnType()));
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown return convention: " + returnConvention);
            }
        }

        private static IcebergInvocationConvention parseInvocationConvention(OperatorType operatorType, Class<?> typeJavaType, Method method, Class<?> expectedReturnType)
        {
            checkArgument(expectedReturnType.isPrimitive(), "Expected return type must be a primitive: %s", expectedReturnType);

            InvocationReturnConvention returnConvention = getReturnConvention(expectedReturnType, operatorType, method);

            List<Class<?>> parameterTypes = Collections.unmodifiableList(Arrays.asList(method.getParameterTypes()));
            List<Annotation[]> parameterAnnotations = Collections.unmodifiableList(Arrays.asList(method.getParameterAnnotations()));

            InvocationArgumentConvention leftArgumentConvention = extractNextArgumentConvention(typeJavaType, parameterTypes, parameterAnnotations, operatorType, method);
            if (leftArgumentConvention.getParameterCount() == parameterTypes.size()) {
                return simpleConvention(returnConvention, leftArgumentConvention);
            }

            InvocationArgumentConvention rightArgumentConvention = extractNextArgumentConvention(
                    typeJavaType,
                    parameterTypes.subList(leftArgumentConvention.getParameterCount(), parameterTypes.size()),
                    parameterAnnotations.subList(leftArgumentConvention.getParameterCount(), parameterTypes.size()),
                    operatorType,
                    method);

            checkArgument(leftArgumentConvention.getParameterCount() + rightArgumentConvention.getParameterCount() == parameterTypes.size(),
                    "Unexpected parameters for %s operator: %s", operatorType, method);

            return simpleConvention(returnConvention, leftArgumentConvention, rightArgumentConvention);
        }

        private static boolean isAnnotationPresent(Annotation[] annotations, Class<? extends Annotation> annotationType)
        {
            return Arrays.stream(annotations).anyMatch(annotationType::isInstance);
        }

        private static InvocationReturnConvention getReturnConvention(Class<?> expectedReturnType, OperatorType operatorType, Method method)
        {
            InvocationReturnConvention returnConvention;
            if (!method.isAnnotationPresent(SqlNullable.class) && method.getReturnType().equals(expectedReturnType)) {
                returnConvention = FAIL_ON_NULL;
            }
            else if (method.isAnnotationPresent(SqlNullable.class) && method.getReturnType().equals(wrap(expectedReturnType))) {
                returnConvention = NULLABLE_RETURN;
            }
            else {
                throw new IllegalArgumentException(format("Expected %s operator to return %s: %s", operatorType, expectedReturnType, method));
            }
            return returnConvention;
        }

        private static InvocationArgumentConvention extractNextArgumentConvention(
                Class<?> typeJavaType,
                List<Class<?>> parameterTypes,
                List<Annotation[]> parameterAnnotations,
                OperatorType operatorType,
                Method method)
        {
            if (isAnnotationPresent(parameterAnnotations.get(0), SqlNullable.class)) {
                if (parameterTypes.get(0).equals(wrap(typeJavaType))) {
                    return BOXED_NULLABLE;
                }
            }
            else if (isAnnotationPresent(parameterAnnotations.get(0), BlockPosition.class)) {
                if (parameterTypes.size() > 1 &&
                        isAnnotationPresent(parameterAnnotations.get(1), BlockIndex.class) &&
                        parameterTypes.get(0).equals(Block.class) &&
                        parameterTypes.get(1).equals(int.class)) {
                    return BLOCK_POSITION;
                }
            }
            else if (parameterTypes.size() > 1 && isAnnotationPresent(parameterAnnotations.get(1), IsNull.class)) {
                if (parameterTypes.size() > 1 &&
                        parameterTypes.get(0).equals(typeJavaType) &&
                        parameterTypes.get(1).equals(boolean.class)) {
                    return NULL_FLAG;
                }
            }
            else {
                if (parameterTypes.get(0).equals(typeJavaType)) {
                    return NEVER_NULL;
                }
            }
            throw new IllegalArgumentException(format("Unexpected parameters for %s operator: %s", operatorType, method));
        }

        private static void checkArgument(boolean test, String message, Object... arguments)
        {
            if (!test) {
                throw new IllegalArgumentException(format(message, arguments));
            }
        }

        private static Class<?> wrap(Class<?> type)
        {
            return methodType(type).wrap().returnType();
        }

        public TypeOperatorDeclaration build()
        {
            if (equalOperators.isEmpty()) {
                if (!hashCodeOperators.isEmpty()) {
                    throw new IllegalStateException("Hash code operators can not be supplied when equal operators are not supplied");
                }
                if (!xxHash64Operators.isEmpty()) {
                    throw new IllegalStateException("xxHash64 operators can not be supplied when equal operators are not supplied");
                }
            }
            else {
                if (xxHash64Operators.isEmpty()) {
                    throw new IllegalStateException("xxHash64 operators must be supplied when equal operators are supplied");
                }
            }
            if (comparisonUnorderedLastOperators.isEmpty() && comparisonUnorderedFirstOperators.isEmpty()) {
                if (!lessThanOperators.isEmpty()) {
                    throw new IllegalStateException("Less-than-operators can not be supplied when comparison operators are not supplied");
                }
                if (!lessThanOrEqualOperators.isEmpty()) {
                    throw new IllegalStateException("Less-than-or-equals operators can not be supplied when comparison operators are not supplied");
                }
            }

            return new TypeOperatorDeclaration(
                    equalOperators,
                    hashCodeOperators,
                    xxHash64Operators,
                    distinctFromOperators,
                    indeterminateOperators,
                    comparisonUnorderedLastOperators,
                    comparisonUnorderedFirstOperators,
                    lessThanOperators,
                    lessThanOrEqualOperators);
        }

        public static TypeOperatorDeclaration extractOperatorDeclaration(Class<?> operatorsClass, Lookup lookup, Class<?> typeJavaType)
        {
            return new Builder(typeJavaType)
                    .addOperators(operatorsClass, lookup)
                    .build();
        }
    }
}
