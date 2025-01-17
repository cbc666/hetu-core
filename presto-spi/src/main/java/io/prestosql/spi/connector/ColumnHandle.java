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
package io.prestosql.spi.connector;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public interface ColumnHandle
{
    /**
     * SqlQueryBuilder requires the column name of the {@link ColumnHandle}.
     * Returns the name of the column accessed by this {@link ColumnHandle}.
     * Most of the existing connectors have a `getColumnName` so that this default
     * method has been defined here to use them in the hetu-main module.
     * However, not every connectors are expected to provide the implementation.
     *
     * @return the column name
     */
    default String getColumnName()
    {
        throw new NotImplementedException();
    }

    default String getTypeName()
    {
        throw new NotImplementedException();
    }

    /**
     * Whether the column represents a partitionColumn?
     *
     * @return
     */
    default boolean isPartitionKey()
    {
        return false;
    }
}
